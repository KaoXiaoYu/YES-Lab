package cn.yeslab.platform.member.controller;

import cn.yeslab.platform.common.api.ApiResponse;
import cn.yeslab.platform.member.api.MemberManagementModels;
import cn.yeslab.platform.member.service.MemberProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
