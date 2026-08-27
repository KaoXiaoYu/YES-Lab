package cn.yeslab.platform.member.controller;

import cn.yeslab.platform.common.api.ApiResponse;
import cn.yeslab.platform.member.api.MemberManagementModels;
import cn.yeslab.platform.member.service.MemberProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/member-profiles")
public class PublicMemberProfileController {

    private final MemberProfileService service;

    public PublicMemberProfileController(MemberProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<MemberManagementModels.PublicProfileView>> profiles() {
        return ApiResponse.ok(service.listPublicProfiles());
    }

    @GetMapping("/{profileId}")
    public ApiResponse<MemberManagementModels.PublicProfileView> profile(@PathVariable UUID profileId) {
        return ApiResponse.ok(service.getPublicProfile(profileId));
    }

    @GetMapping("/{profileId}/avatar")
    public ResponseEntity<Resource> avatar(@PathVariable UUID profileId) {
        MemberProfileService.AvatarDownload avatar = service.avatar(profileId);
        MediaType contentType;
        try {
            contentType = MediaType.parseMediaType(avatar.contentType());
        } catch (Exception ignored) {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(contentType)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(avatar.originalName(), StandardCharsets.UTF_8).build().toString())
                .body(avatar.resource());
    }
}
