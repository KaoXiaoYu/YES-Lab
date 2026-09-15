package cn.yeslab.platform.publicsite.cms.controller;

import cn.yeslab.platform.common.api.ApiResponse;
import cn.yeslab.platform.publicsite.cms.service.HomepageModelStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.UUID;

@RestController
public class HomepageModelController {

    private static final MediaType GLB_MEDIA_TYPE = MediaType.parseMediaType("model/gltf-binary");

    private final HomepageModelStorageService models;

    public HomepageModelController(HomepageModelStorageService models) {
        this.models = models;
    }

    @PostMapping("/api/v1/admin/homepage/models")
    @PreAuthorize("hasAuthority('CONTENT_MANAGE')")
    public ApiResponse<ModelView> upload(@RequestParam("model") MultipartFile model) {
        return ApiResponse.ok(new ModelView(models.upload(model)));
    }

    @GetMapping("/api/v1/public/homepage/models/{id}")
    public ResponseEntity<Resource> model(@PathVariable UUID id) {
        HomepageModelStorageService.StoredModelResource model = models.resource(id);
        return ResponseEntity.ok()
                .contentType(GLB_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + model.filename() + "\"")
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(model.resource());
    }

    public record ModelView(String modelUrl) {
    }
}
