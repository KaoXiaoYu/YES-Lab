package cn.yeslab.platform.member.controller;

import cn.yeslab.platform.common.api.ApiResponse;
import cn.yeslab.platform.member.api.MemberProfileModels;
import cn.yeslab.platform.member.service.MemberProfileService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
