package cn.yeslab.platform.project.service;

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
public class ProjectCoverStorageService {
    private static final long COVER_LIMIT = 8L * 1024 * 1024;
    private static final Set<String> COVER_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final Path coverDirectory;

    public ProjectCoverStorageService(@Value("${yeslab.storage.projects-directory:./data/projects}") String directory) {
        this.coverDirectory = Path.of(directory).toAbsolutePath().normalize().resolve("covers");
        try {
            Files.createDirectories(coverDirectory);
        } catch (IOException error) {
            throw new IllegalStateException("无法初始化项目主图目录", error);
        }
    }

    public StoredCover store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请选择项目主图");
        }
        if (file.getSize() > COVER_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "项目主图不能超过 8MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!COVER_TYPES.contains(contentType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "项目主图仅支持 JPG、PNG 或 WebP");
        }
        verifySignature(file, contentType);
        String extension = switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
        String storedName = UUID.randomUUID() + extension;
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, coverDirectory.resolve(storedName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "项目主图保存失败");
        }
        String originalName = file.getOriginalFilename() == null
                ? "project-cover" + extension
                : Path.of(file.getOriginalFilename()).getFileName().toString();
        return new StoredCover(storedName, originalName, contentType, file.getSize());
    }

    public Resource resource(String storedName) {
        Path path = coverDirectory.resolve(storedName).normalize();
        if (!path.startsWith(coverDirectory) || !Files.isRegularFile(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "项目主图不存在");
        }
        return new FileSystemResource(path);
    }

    public void delete(String storedName) {
        if (storedName == null) return;
        try {
            Files.deleteIfExists(coverDirectory.resolve(storedName).normalize());
        } catch (IOException ignored) {
        }
    }

    private void verifySignature(MultipartFile file, String contentType) {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(12);
            boolean valid = switch (contentType) {
                case "image/png" -> header.length >= 8
                        && (header[0] & 0xff) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G';
                case "image/jpeg" -> header.length >= 3
                        && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8 && (header[2] & 0xff) == 0xff;
                case "image/webp" -> header.length >= 12
                        && new String(header, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                        && new String(header, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
                default -> false;
            };
            if (!valid) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "项目主图文件内容与格式不匹配");
            }
        } catch (IOException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "项目主图读取失败");
        }
    }

    public record StoredCover(String storedName, String originalName, String contentType, long sizeBytes) {
    }
}
