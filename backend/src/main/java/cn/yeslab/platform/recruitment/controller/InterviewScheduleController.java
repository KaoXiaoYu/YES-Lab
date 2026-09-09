package cn.yeslab.platform.recruitment.controller;

import cn.yeslab.platform.common.api.ApiResponse;
import cn.yeslab.platform.recruitment.api.InterviewScheduleModels;
import cn.yeslab.platform.recruitment.service.InterviewScheduleService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recruitment/interviews")
public class InterviewScheduleController {
    private final InterviewScheduleService service;

    public InterviewScheduleController(InterviewScheduleService service) { this.service = service; }

    @GetMapping
    public ApiResponse<InterviewScheduleModels.ApplicantScheduleView> schedule(Authentication authentication) {
        return ApiResponse.ok(service.applicantSchedule(authentication));
    }

    @PostMapping("/sessions/{sessionId}/book")
    public ApiResponse<InterviewScheduleModels.ApplicantScheduleView> book(Authentication authentication, @PathVariable UUID sessionId) {
        return ApiResponse.ok(service.book(authentication, sessionId));
    }

    @DeleteMapping("/booking")
    public ApiResponse<InterviewScheduleModels.ApplicantScheduleView> cancel(Authentication authentication) {
        return ApiResponse.ok(service.cancelOwnBooking(authentication));
    }
}
