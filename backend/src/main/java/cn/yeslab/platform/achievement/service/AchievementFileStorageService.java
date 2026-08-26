package cn.yeslab.platform.achievement.service;

import cn.yeslab.platform.common.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
public class AchievementFileStorageService {
    private static final long CERTIFICATE_LIMIT = 10L * 1024 * 1024;
    private static final long IMAGE_LIMIT = 8L * 1024 * 1024;
    private static final Set<String> CERTIFICATE_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

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

    public StoredFile storeCertificate(MultipartFile file) { return store(file, certificateDirectory, CERTIFICATE_TYPES, CERTIFICATE_LIMIT, "证书"); }
    public StoredFile storeImage(MultipartFile file) { return store(file, imageDirectory, IMAGE_TYPES, IMAGE_LIMIT, "比赛图片"); }
    public Resource certificate(String storedName) { return resource(certificateDirectory, storedName); }
    public Resource image(String storedName) { return resource(imageDirectory, storedName); }
    public void deleteCertificate(String storedName) { delete(certificateDirectory, storedName); }
    public void deleteImage(String storedName) { delete(imageDirectory, storedName); }

    private StoredFile store(MultipartFile file, Path directory, Set<String> allowedTypes, long limit, String label) {
        if (file == null || file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "请上传" + label);
        if (file.getSize() > limit) throw new ApiException(HttpStatus.BAD_REQUEST, label + "文件过大");
        String contentType = detectContentType(file, label);
        if (!allowedTypes.contains(contentType)) throw new ApiException(HttpStatus.BAD_REQUEST, label + "格式不支持");
        String extension = switch (contentType) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
        String storedName = UUID.randomUUID() + extension;
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, directory.resolve(storedName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, label + "保存失败");
        }
        String original = file.getOriginalFilename() == null ? label + extension : Path.of(file.getOriginalFilename()).getFileName().toString();
        return new StoredFile(storedName, original, contentType, file.getSize());
    }

    private String detectContentType(MultipartFile file, String label) {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(12);
            if (header.length >= 4 && header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F') {
                return "application/pdf";
            }
            if (header.length >= 8 && (header[0] & 0xff) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
                return "image/png";
            }
            if (header.length >= 3 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8 && (header[2] & 0xff) == 0xff) {
                return "image/jpeg";
            }
            if (header.length >= 12 && new String(header, 0, 4).equals("RIFF") && new String(header, 8, 4).equals("WEBP")) {
                return "image/webp";
            }
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "文件内容与格式不匹配");
        } catch (IOException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "读取失败");
        }
    }

    private Resource resource(Path directory, String storedName) {
        Path path = directory.resolve(storedName).normalize();
        if (!path.startsWith(directory) || !Files.isRegularFile(path)) throw new ApiException(HttpStatus.NOT_FOUND, "文件不存在");
        return new FileSystemResource(path);
    }

    private void delete(Path directory, String storedName) {
        if (storedName == null) return;
        try { Files.deleteIfExists(directory.resolve(storedName).normalize()); } catch (IOException ignored) { }
    }

    public record StoredFile(String storedName, String originalName, String contentType, long sizeBytes) {}
}
