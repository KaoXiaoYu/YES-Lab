package cn.yeslab.platform.recruitment.controller;

import cn.yeslab.platform.common.api.ApiResponse;
import cn.yeslab.platform.recruitment.api.RecruitmentModels;
import cn.yeslab.platform.recruitment.service.RecruitmentService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recruitment")
public class RecruitmentController {

    private final RecruitmentService service;

    public RecruitmentController(RecruitmentService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public ApiResponse<RecruitmentModels.ApplicationView> mine(Authentication authentication) {
        return ApiResponse.ok(service.getOwn(authentication));
    }

    @PutMapping("/me")
    public ApiResponse<RecruitmentModels.ApplicationView> save(
            Authentication authentication,
            @Valid @RequestBody RecruitmentModels.ApplicationRequest request
    ) {
        return ApiResponse.ok(service.saveOwn(authentication, request));
    }
}
