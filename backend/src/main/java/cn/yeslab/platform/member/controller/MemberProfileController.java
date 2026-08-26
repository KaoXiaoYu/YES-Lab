package cn.yeslab.platform.member.controller;

import cn.yeslab.platform.common.api.ApiResponse;
import cn.yeslab.platform.member.api.MemberProfileModels;
import cn.yeslab.platform.member.service.MemberProfileService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/api/v1/member/profile")
public class MemberProfileController {

    private final MemberProfileService service;

    public MemberProfileController(MemberProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<MemberProfileModels.ProfileView> profile(Authentication authentication) {
        return ApiResponse.ok(service.getOwnProfile(authentication));
    }

    @PutMapping
    public ApiResponse<MemberProfileModels.ProfileView> update(
            Authentication authentication,
            @Valid @RequestBody MemberProfileModels.UpdateProfileRequest request
    ) {
        return ApiResponse.ok(service.updateOwnProfile(authentication, request));
    }

    @GetMapping("/showcase")
    public ApiResponse<MemberProfileModels.ShowcaseSettings> showcase(Authentication authentication) {
        return ApiResponse.ok(service.getOwnShowcase(authentication));
    }

    @PutMapping("/showcase")
    public ApiResponse<MemberProfileModels.ShowcaseSettings> updateShowcase(
            Authentication authentication,
            @Valid @RequestBody MemberProfileModels.UpdateShowcaseRequest request
    ) {
        return ApiResponse.ok(service.updateOwnShowcase(authentication, request));
    }

    @PutMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MemberProfileModels.ProfileView> replaceAvatar(
            Authentication authentication,
            @RequestPart("avatar") MultipartFile avatar
    ) {
        return ApiResponse.ok(service.replaceOwnAvatar(authentication, avatar));
    }

    @DeleteMapping("/avatar")
    public ApiResponse<MemberProfileModels.ProfileView> deleteAvatar(Authentication authentication) {
        return ApiResponse.ok(service.deleteOwnAvatar(authentication));
    }
}
