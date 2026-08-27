package cn.yeslab.platform.achievement.service;

import cn.yeslab.platform.common.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AchievementFileStorageService {
    private static final long CERTIFICATE_LIMIT = 10L * 1024 * 1024;
    private static final long IMAGE_LIMIT = 8L * 1024 * 1024;
    private static final Set<String> CERTIFICATE_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> JPEG_CLIENT_TYPES = Set.of("image/jpeg", "image/jpg", "image/pjpeg", "image/bmp", "application/octet-stream");
    private static final int MAX_NORMALIZED_EDGE = 2560;
    private static final long MAX_IMAGE_PIXELS = 40_000_000L;

    private final Path certificateDirectory;
    private final Path imageDirectory;

    public AchievementFileStorageService(@Value("${yeslab.storage.achievements-directory:./data/achievements}") String directory) {
        Path root = Path.of(directory).toAbsolutePath().normalize();
        this.certificateDirectory = root.resolve("certificates");
        this.imageDirectory = root.resolve("competition-images");
        try {
            Files.createDirectories(certificateDirectory);
            Files.createDirectories(imageDirectory);
        } catch (IOException error) {
            throw new IllegalStateException("无法初始化成果文件目录", error);
        }
    }

    public StoredFile storeCertificate(MultipartFile file) { return store(file, certificateDirectory, CERTIFICATE_TYPES, CERTIFICATE_LIMIT, "证书", true); }
    public StoredFile storeImage(MultipartFile file) { return store(file, imageDirectory, IMAGE_TYPES, IMAGE_LIMIT, "比赛图片", true); }
    public Resource certificate(String storedName) { return resource(certificateDirectory, storedName, "证书"); }
    public Resource image(String storedName) { return resource(imageDirectory, storedName, "比赛图片"); }
    public void deleteCertificate(String storedName) { delete(certificateDirectory, storedName); }
    public void deleteImage(String storedName) { delete(imageDirectory, storedName); }

    private StoredFile store(MultipartFile file, Path directory, Set<String> allowedTypes, long limit, String label, boolean jpegFallback) {
        if (file == null || file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "请上传" + label);
        if (file.getSize() > limit) throw new ApiException(HttpStatus.BAD_REQUEST, label + "文件过大");
        byte[] source;
        try {
            source = file.getBytes();
        } catch (IOException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "读取失败");
        }
        DetectedFile detected = detectContentType(file, source, label, jpegFallback);
        if (!allowedTypes.contains(detected.contentType())) throw new ApiException(HttpStatus.BAD_REQUEST, label + "格式不支持");
        String extension = switch (detected.contentType()) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
        String storedName = UUID.randomUUID() + extension;
        byte[] storedBytes = detected.convertToJpeg()
                ? encodeJpeg(source, label)
                : detected.jpegOffset() > 0
                        ? Arrays.copyOfRange(source, detected.jpegOffset(), source.length)
                        : source;
        try {
            Files.write(directory.resolve(storedName), storedBytes);
        } catch (IOException error) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, label + "保存失败");
        }
        String original = file.getOriginalFilename() == null ? label + extension : Path.of(file.getOriginalFilename()).getFileName().toString();
        return new StoredFile(storedName, original, detected.contentType(), storedBytes.length);
    }

    private DetectedFile detectContentType(MultipartFile file, byte[] source, String label, boolean jpegFallback) {
        byte[] header = Arrays.copyOf(source, Math.min(source.length, 4096));
        if (header.length >= 4 && header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F') {
            return new DetectedFile("application/pdf", 0, false);
        }
        if (header.length >= 8 && (header[0] & 0xff) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
            return new DetectedFile("image/png", 0, false);
        }
        int jpegOffset = findJpegStart(header);
        if (jpegOffset >= 0) {
            return new DetectedFile("image/jpeg", jpegOffset, false);
        }
        if (header.length >= 12 && new String(header, 0, 4).equals("RIFF") && new String(header, 8, 4).equals("WEBP")) {
            return new DetectedFile("image/webp", 0, false);
        }
        if (jpegFallback && hasJpegName(file.getOriginalFilename())
                && JPEG_CLIENT_TYPES.contains(normalizedClientType(file.getContentType()))
                && isBitmap(header)) {
            // 某些 Windows/扫描软件会把 BMP 内容保存成 .jpg。此时必须真正解码并转成
            // JPEG，不能只修改 Content-Type，否则 nosniff 浏览器会拒绝显示。
            return new DetectedFile("image/jpeg", 0, true);
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, label + "文件内容与格式不匹配");
    }

    private int findJpegStart(byte[] bytes) {
        for (int index = 0; index <= bytes.length - 3; index++) {
            if ((bytes[index] & 0xff) == 0xff && (bytes[index + 1] & 0xff) == 0xd8 && (bytes[index + 2] & 0xff) == 0xff) return index;
        }
        return -1;
    }

    private boolean hasJpegName(String originalName) {
        if (originalName == null) return false;
        String normalized = originalName.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".jpg") || normalized.endsWith(".jpeg");
    }

    private String normalizedClientType(String contentType) {
        return contentType == null ? "application/octet-stream" : contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
    }

    private boolean isBitmap(byte[] bytes) {
        return bytes.length >= 2 && bytes[0] == 'B' && bytes[1] == 'M';
    }

    private byte[] encodeJpeg(byte[] source, String label) {
        BufferedImage decoded;
        try (ByteArrayInputStream input = new ByteArrayInputStream(source)) {
            decoded = ImageIO.read(input);
        } catch (IOException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "图片无法解码");
        }
        if (decoded == null || decoded.getWidth() <= 0 || decoded.getHeight() <= 0
                || (long) decoded.getWidth() * decoded.getHeight() > MAX_IMAGE_PIXELS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "图片无法解码或尺寸过大");
        }

        double scale = Math.min(1D, (double) MAX_NORMALIZED_EDGE / Math.max(decoded.getWidth(), decoded.getHeight()));
        int width = Math.max(1, (int) Math.round(decoded.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(decoded.getHeight() * scale));
        BufferedImage normalized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = normalized.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(decoded, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "服务器缺少 JPEG 编码器");
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(0.88F);
            }
            writer.write(null, new IIOImage(normalized, null, null), params);
            return output.toByteArray();
        } catch (IOException error) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, label + "转为 JPEG 失败");
        } finally {
            writer.dispose();
        }
    }

    private Resource resource(Path directory, String storedName, String label) {
        Path path = directory.resolve(storedName).normalize();
        if (!path.startsWith(directory) || !Files.isRegularFile(path)) throw new ApiException(HttpStatus.NOT_FOUND, "文件不存在");
        repairLegacyBitmap(path, label);
        return new FileSystemResource(path);
    }

    private synchronized void repairLegacyBitmap(Path path, String label) {
        if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jpg")) return;
        byte[] header;
        try (InputStream input = Files.newInputStream(path)) {
            header = input.readNBytes(2);
        } catch (IOException error) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, label + "读取失败");
        }
        if (!isBitmap(header)) return;

        Path temporary = null;
        try {
            byte[] normalized = encodeJpeg(Files.readAllBytes(path), label);
            temporary = Files.createTempFile(path.getParent(), ".normalized-", ".jpg");
            Files.write(temporary, normalized);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, label + "存量图片修复失败");
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
        }
    }

    private void delete(Path directory, String storedName) {
        if (storedName == null) return;
        try { Files.deleteIfExists(directory.resolve(storedName).normalize()); } catch (IOException ignored) { }
    }

    private record DetectedFile(String contentType, int jpegOffset, boolean convertToJpeg) {}
    public record StoredFile(String storedName, String originalName, String contentType, long sizeBytes) {}
}
