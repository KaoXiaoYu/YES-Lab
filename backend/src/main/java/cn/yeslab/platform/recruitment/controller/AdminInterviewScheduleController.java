package cn.yeslab.platform.recruitment.controller;

import cn.yeslab.platform.common.api.ApiResponse;
import cn.yeslab.platform.recruitment.api.InterviewScheduleModels;
import cn.yeslab.platform.recruitment.service.InterviewScheduleService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/recruitment/interview-sessions")
public class AdminInterviewScheduleController {
    private final InterviewScheduleService service;

    public AdminInterviewScheduleController(InterviewScheduleService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<InterviewScheduleModels.AdminSessionView>> list(Authentication authentication) {
        return ApiResponse.ok(service.listAdmin(authentication));
    }

    @PostMapping
    public ApiResponse<InterviewScheduleModels.AdminSessionView> create(Authentication authentication,
            @Valid @RequestBody InterviewScheduleModels.SessionRequest request) {
        return ApiResponse.ok(service.create(authentication, request));
    }

    @PutMapping("/{sessionId}")
    public ApiResponse<InterviewScheduleModels.AdminSessionView> update(Authentication authentication,
            @PathVariable UUID sessionId, @Valid @RequestBody InterviewScheduleModels.SessionRequest request) {
        return ApiResponse.ok(service.update(authentication, sessionId, request));
    }

    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> cancel(Authentication authentication, @PathVariable UUID sessionId) {
        service.cancel(authentication, sessionId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{sessionId}/call-next")
    public ApiResponse<InterviewScheduleModels.AdminSessionView> callNext(Authentication authentication, @PathVariable UUID sessionId) {
        return ApiResponse.ok(service.callNext(authentication, sessionId));
    }

    @PostMapping("/{sessionId}/bookings/{bookingId}/start")
    public ApiResponse<InterviewScheduleModels.AdminSessionView> start(Authentication authentication, @PathVariable UUID sessionId,
            @PathVariable UUID bookingId) {
        return ApiResponse.ok(service.startInterview(authentication, sessionId, bookingId));
    }

    @PostMapping("/{sessionId}/bookings/{bookingId}/no-show")
    public ApiResponse<InterviewScheduleModels.AdminSessionView> noShow(Authentication authentication, @PathVariable UUID sessionId,
            @PathVariable UUID bookingId) {
        return ApiResponse.ok(service.noShow(authentication, sessionId, bookingId));
    }

    @PostMapping("/{sessionId}/bookings/{bookingId}/complete")
    public ApiResponse<InterviewScheduleModels.AdminSessionView> complete(Authentication authentication, @PathVariable UUID sessionId,
            @PathVariable UUID bookingId, @Valid @RequestBody InterviewScheduleModels.InterviewResultRequest request) {
        return ApiResponse.ok(service.completeInterview(authentication, sessionId, bookingId, request));
    }

    @PostMapping("/{sessionId}/end-early")
    public ApiResponse<InterviewScheduleModels.AdminSessionView> endEarly(Authentication authentication, @PathVariable UUID sessionId) {
        return ApiResponse.ok(service.endEarly(authentication, sessionId));
    }
}
