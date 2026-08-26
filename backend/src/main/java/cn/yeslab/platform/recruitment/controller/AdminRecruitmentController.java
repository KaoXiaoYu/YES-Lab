package cn.yeslab.platform.recruitment.controller;

import cn.yeslab.platform.common.api.ApiResponse;
import cn.yeslab.platform.recruitment.api.RecruitmentModels;
import cn.yeslab.platform.recruitment.service.RecruitmentService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/recruitment")
public class AdminRecruitmentController {

    private final RecruitmentService service;

    public AdminRecruitmentController(RecruitmentService service) {
        this.service = service;
    }

    @GetMapping("/applications")
    public ApiResponse<List<RecruitmentModels.ApplicationView>> applications() {
        return ApiResponse.ok(service.listApplications());
    }

    @GetMapping("/interviewers")
    public ApiResponse<List<RecruitmentModels.InterviewerView>> interviewers() {
        return ApiResponse.ok(service.listInterviewers());
    }

    @PatchMapping("/applications/{applicationId}/stage")
    public ApiResponse<RecruitmentModels.ApplicationView> changeStage(
            Authentication authentication,
            @PathVariable UUID applicationId,
            @Valid @RequestBody RecruitmentModels.StageChangeRequest request
    ) {
        return ApiResponse.ok(service.changeStage(authentication, applicationId, request));
    }

    @PutMapping("/applications/{applicationId}/interview")
    public ApiResponse<RecruitmentModels.ApplicationView> interview(
            Authentication authentication,
            @PathVariable UUID applicationId,
            @Valid @RequestBody RecruitmentModels.InterviewRequest request
    ) {
        return ApiResponse.ok(service.recordInterview(authentication, applicationId, request));
    }

    @PostMapping("/applications/{applicationId}/convert")
    public ApiResponse<RecruitmentModels.ApplicationView> convert(
            Authentication authentication,
            @PathVariable UUID applicationId,
            @Valid @RequestBody RecruitmentModels.ConvertMemberRequest request
    ) {
        return ApiResponse.ok(service.convertToMember(authentication, applicationId, request));
    }
}
