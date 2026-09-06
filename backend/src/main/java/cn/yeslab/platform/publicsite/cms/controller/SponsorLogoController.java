package cn.yeslab.platform.publicsite.cms.controller;

import cn.yeslab.platform.common.api.ApiResponse;
import cn.yeslab.platform.publicsite.cms.service.SponsorLogoStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.Duration;
import java.util.UUID;

@RestController
public class SponsorLogoController {
    private final SponsorLogoStorageService logos;
    public SponsorLogoController(SponsorLogoStorageService logos) { this.logos = logos; }

    @PostMapping("/api/v1/admin/homepage/sponsors/logo")
    @PreAuthorize("hasAuthority('CONTENT_MANAGE')")
    public ApiResponse<LogoView> upload(@RequestParam("logo") MultipartFile logo) {
        return ApiResponse.ok(new LogoView(logos.upload(logo)));
    }

    @GetMapping("/api/v1/public/sponsors/logos/{id}")
    public ResponseEntity<Resource> image(@PathVariable UUID id) {
        var image = logos.resource(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(image.resource());
    }
    public record LogoView(String logoUrl) {}
}
