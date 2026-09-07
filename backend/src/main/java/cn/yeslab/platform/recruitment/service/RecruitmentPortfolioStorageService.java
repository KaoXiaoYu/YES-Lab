package cn.yeslab.platform.recruitment.service;

import cn.yeslab.platform.common.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class RecruitmentPortfolioStorageService {
    private static final long IMAGE_LIMIT = 5L * 1024 * 1024;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private final Path directory;

    public RecruitmentPortfolioStorageService(@Value("${yeslab.storage.recruitment-directory:./data/recruitment}") String root) {
        directory = Path.of(root).toAbsolutePath().normalize().resolve("portfolio-images");
        try { Files.createDirectories(directory); }
        catch (IOException error) { throw new IllegalStateException("作品图片目录初始化失败", error); }
    }

    public StoredImage store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "请选择作品图片");
        if (file.getSize() > IMAGE_LIMIT) throw new ApiException(HttpStatus.BAD_REQUEST, "单张作品图片不能超过 5MB");
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!IMAGE_TYPES.contains(type)) throw new ApiException(HttpStatus.BAD_REQUEST, "作品图片仅支持 JPG、PNG 或 WebP");
        verifySignature(file, type);
        String extension = type.equals("image/png") ? ".png" : type.equals("image/webp") ? ".webp" : ".jpg";
        String storedName = UUID.randomUUID() + extension;
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, directory.resolve(storedName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "作品图片保存失败"); }
        String original = file.getOriginalFilename() == null ? "portfolio" + extension
                : Path.of(file.getOriginalFilename()).getFileName().toString();
        return new StoredImage(storedName, original, type, file.getSize());
    }

    public Resource resource(String storedName) {
        Path path = directory.resolve(storedName).normalize();
        if (!path.startsWith(directory) || !Files.isRegularFile(path)) throw new ApiException(HttpStatus.NOT_FOUND, "作品图片不存在");
        return new FileSystemResource(path);
    }
    public void delete(String storedName) { try { Files.deleteIfExists(directory.resolve(storedName).normalize()); } catch (IOException ignored) { } }

    private void verifySignature(MultipartFile file, String type) {
        try (InputStream input = file.getInputStream()) {
            byte[] h = input.readNBytes(12);
            boolean valid = switch (type) {
                case "image/png" -> h.length >= 8 && (h[0] & 255) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
                case "image/jpeg" -> h.length >= 3 && (h[0] & 255) == 0xff && (h[1] & 255) == 0xd8 && (h[2] & 255) == 0xff;
                case "image/webp" -> h.length >= 12 && new String(h, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                        && new String(h, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
                default -> false;
            };
            if (!valid) throw new ApiException(HttpStatus.BAD_REQUEST, "作品图片内容与格式不匹配");
        } catch (IOException error) { throw new ApiException(HttpStatus.BAD_REQUEST, "作品图片读取失败"); }
    }
    public record StoredImage(String storedName, String originalName, String contentType, long sizeBytes) { }
}
