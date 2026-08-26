package cn.yeslab.platform.member.service;

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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class MemberAvatarStorageService {

    private static final long AVATAR_LIMIT = 4L * 1024 * 1024;
    private static final Set<String> AVATAR_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final List<String> EXTENSIONS = List.of(".jpg", ".png", ".webp");

    private final Path avatarDirectory;

    public MemberAvatarStorageService(@Value("${yeslab.storage.members-directory:./data/members}") String directory) {
        this.avatarDirectory = Path.of(directory).toAbsolutePath().normalize().resolve("avatars");
        try {
            Files.createDirectories(avatarDirectory);
        } catch (IOException error) {
            throw new IllegalStateException("无法初始化成员头像目录", error);
        }
    }

    public StoredAvatar store(UUID profileId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "请选择头像图片");
        if (file.getSize() > AVATAR_LIMIT) throw new ApiException(HttpStatus.BAD_REQUEST, "头像图片不能超过 4MB");
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!AVATAR_TYPES.contains(contentType)) throw new ApiException(HttpStatus.BAD_REQUEST, "头像仅支持 JPG、PNG 或 WebP");
        verifySignature(file, contentType);

        String extension = extensionFor(contentType);
        String storedName = profileId + extension;
        Path temporary;
        try {
            temporary = Files.createTempFile(avatarDirectory, ".avatar-", ".upload");
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            moveIntoPlace(temporary, avatarDirectory.resolve(storedName));
            deleteOtherFormats(profileId, storedName);
        } catch (IOException error) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "头像保存失败");
        }
        return new StoredAvatar(storedName, contentType);
    }

    public StoredAvatarResource resource(UUID profileId) {
        for (String extension : EXTENSIONS) {
            Path path = avatarDirectory.resolve(profileId + extension).normalize();
            if (path.startsWith(avatarDirectory) && Files.isRegularFile(path)) {
                return new StoredAvatarResource(new FileSystemResource(path), contentTypeFor(extension), "avatar" + extension);
            }
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "头像不存在");
    }

    public void delete(UUID profileId) {
        for (String extension : EXTENSIONS) {
            try {
                Files.deleteIfExists(avatarDirectory.resolve(profileId + extension).normalize());
            } catch (IOException error) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "头像删除失败");
            }
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
            if (!valid) throw new ApiException(HttpStatus.BAD_REQUEST, "头像文件内容与格式不匹配");
        } catch (IOException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "头像图片读取失败");
        }
    }

    private void moveIntoPlace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteOtherFormats(UUID profileId, String keptName) throws IOException {
        for (String extension : EXTENSIONS) {
            String candidate = profileId + extension;
            if (!candidate.equals(keptName)) Files.deleteIfExists(avatarDirectory.resolve(candidate));
        }
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private String contentTypeFor(String extension) {
        return switch (extension) {
            case ".png" -> "image/png";
            case ".webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }

    public record StoredAvatar(String storedName, String contentType) {
    }

    public record StoredAvatarResource(Resource resource, String contentType, String originalName) {
    }
}
