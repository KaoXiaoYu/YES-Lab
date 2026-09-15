package cn.yeslab.platform.publicsite.cms.service;

import cn.yeslab.platform.common.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class HomepageModelStorageService {

    private static final long MODEL_LIMIT = 20L * 1024 * 1024;
    private static final Set<String> ACCEPTED_CONTENT_TYPES = Set.of(
            "model/gltf-binary",
            "application/octet-stream"
    );

    private final Path modelDirectory;

    public HomepageModelStorageService(
            @Value("${yeslab.storage.homepage-models-directory:./data/homepage-models}") String directory
    ) {
        modelDirectory = Path.of(directory).toAbsolutePath().normalize();
        try {
            Files.createDirectories(modelDirectory);
        } catch (IOException error) {
            throw new IllegalStateException("无法初始化首页 3D 模型目录", error);
        }
    }

    public String upload(MultipartFile model) {
        validate(model);
        UUID id = UUID.randomUUID();
        Path temporary = null;
        try {
            temporary = Files.createTempFile(modelDirectory, ".homepage-model-", ".upload");
            try (InputStream input = model.getInputStream()) {
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            moveIntoPlace(temporary, modelDirectory.resolve(id + ".glb"));
        } catch (IOException error) {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The failed upload is already being reported to the caller.
                }
            }
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "首页 3D 模型保存失败");
        }
        return "/api/v1/public/homepage/models/" + id;
    }

    public StoredModelResource resource(UUID id) {
        Path path = modelDirectory.resolve(id + ".glb").normalize();
        if (!path.startsWith(modelDirectory) || !Files.isRegularFile(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "首页 3D 模型不存在");
        }
        return new StoredModelResource(new FileSystemResource(path), id + ".glb");
    }

    private void validate(MultipartFile model) {
        if (model == null || model.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请选择 GLB 模型文件");
        }
        if (model.getSize() > MODEL_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GLB 模型不能超过 20MB");
        }
        String filename = model.getOriginalFilename() == null ? "" : model.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".glb")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "首页 3D 模型仅支持 .glb 文件");
        }
        String contentType = model.getContentType() == null ? "" : model.getContentType().toLowerCase(Locale.ROOT);
        if (!contentType.isBlank() && !ACCEPTED_CONTENT_TYPES.contains(contentType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "首页 3D 模型仅支持 GLB 二进制格式");
        }
        verifyGlbHeader(model);
    }

    private void verifyGlbHeader(MultipartFile model) {
        try (InputStream input = model.getInputStream()) {
            byte[] header = input.readNBytes(12);
            boolean magicMatches = header.length == 12
                    && new String(header, 0, 4, StandardCharsets.US_ASCII).equals("glTF");
            if (!magicMatches) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "GLB 文件内容与格式不匹配");
            }
            ByteBuffer fields = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            fields.position(4);
            long version = Integer.toUnsignedLong(fields.getInt());
            long declaredLength = Integer.toUnsignedLong(fields.getInt());
            if (version != 2 || declaredLength != model.getSize()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "GLB 文件结构无效，仅支持 GLB 2.0");
            }
        } catch (IOException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GLB 模型读取失败");
        }
    }

    private void moveIntoPlace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record StoredModelResource(Resource resource, String filename) {
    }
}
