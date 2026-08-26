package cn.yeslab.platform.member.controller;

import cn.yeslab.platform.common.api.ApiResponse;
import cn.yeslab.platform.member.api.MemberManagementModels;
import cn.yeslab.platform.member.api.MemberProfileModels;
import cn.yeslab.platform.member.service.MemberProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

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

    @PostMapping("/core-students")
    public ApiResponse<MemberProfileModels.ProfileView> createCoreStudent(
            @Valid @RequestBody MemberManagementModels.CreateCoreStudentRequest request
    ) {
        return ApiResponse.ok(service.createCoreStudent(request));
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

    @PutMapping(value = "/{profileId}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MemberProfileModels.ProfileView> replaceAvatar(
            @PathVariable UUID profileId,
            @RequestPart("avatar") MultipartFile avatar
    ) {
        return ApiResponse.ok(service.replaceManagedAvatar(profileId, avatar));
    }

    @DeleteMapping("/{profileId}/avatar")
    public ApiResponse<MemberProfileModels.ProfileView> deleteAvatar(@PathVariable UUID profileId) {
        return ApiResponse.ok(service.deleteManagedAvatar(profileId));
    }
}
