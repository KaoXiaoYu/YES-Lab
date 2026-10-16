package cn.yeslab.platform.recruitment.form.controller;

import cn.yeslab.platform.common.api.ApiResponse;
import cn.yeslab.platform.recruitment.form.api.RecruitmentFormModels.*;
import cn.yeslab.platform.recruitment.form.service.RecruitmentFormService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/recruitment/form")
public class AdminRecruitmentFormController {
    private final RecruitmentFormService service;
    public AdminRecruitmentFormController(RecruitmentFormService service) { this.service = service; }
    @GetMapping public ApiResponse<AdminView> form() { return ApiResponse.ok(service.adminView()); }
    @PutMapping("/draft") public ApiResponse<AdminView> save(Authentication authentication, @Valid @RequestBody DraftRequest request) {
        return ApiResponse.ok(service.saveDraft(authentication, request));
    }
    @PostMapping("/publish") public ApiResponse<PublishedVersion> publish(Authentication authentication, @Valid @RequestBody PublishRequest request) {
        return ApiResponse.ok(service.publish(authentication, request));
    }
    @GetMapping("/versions/{versionId}") public ApiResponse<PublishedVersion> version(@PathVariable UUID versionId) {
        return ApiResponse.ok(service.version(versionId));
    }
}
