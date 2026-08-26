package cn.yeslab.platform.member.controller;

import cn.yeslab.platform.common.api.ApiResponse;
import cn.yeslab.platform.member.api.MemberManagementModels;
import cn.yeslab.platform.member.api.MemberProfileModels;
import cn.yeslab.platform.member.service.MemberProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/members")
public class AdminMemberController {

    private final MemberProfileService service;

    public AdminMemberController(MemberProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<MemberProfileModels.ProfileView>> members() {
        return ApiResponse.ok(service.listManagedMembers());
    }

    @GetMapping("/{profileId}")
    public ApiResponse<MemberProfileModels.ProfileView> member(@PathVariable UUID profileId) {
        return ApiResponse.ok(service.getManagedMember(profileId));
    }

    @PutMapping("/{profileId}")
    public ApiResponse<MemberProfileModels.ProfileView> update(
            @PathVariable UUID profileId,
            @Valid @RequestBody MemberManagementModels.UpdateMemberRequest request
    ) {
        return ApiResponse.ok(service.updateManagedMember(profileId, request));
    }
}
