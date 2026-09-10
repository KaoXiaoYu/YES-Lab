package cn.yeslab.platform.notification.controller;

import cn.yeslab.platform.common.api.ApiResponse;
import cn.yeslab.platform.notification.api.NotificationModels;
import cn.yeslab.platform.notification.service.MelinaVisibilityService;
import cn.yeslab.platform.notification.service.NotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService service;
    private final MelinaVisibilityService visibility;

    public NotificationController(NotificationService service, MelinaVisibilityService visibility) {
        this.service = service;
        this.visibility = visibility;
    }

    @GetMapping("/visibility")
    public ApiResponse<NotificationModels.VisibilityView> visibility(Authentication authentication) {
        return ApiResponse.ok(visibility.visibility(authentication));
    }

    @GetMapping
    public ApiResponse<NotificationModels.InboxView> inbox(Authentication authentication) {
        return ApiResponse.ok(service.inbox(authentication));
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<NotificationModels.InboxView> markRead(Authentication authentication, @PathVariable UUID notificationId) {
        return ApiResponse.ok(service.markRead(authentication, notificationId));
    }

    @PatchMapping("/read-all")
    public ApiResponse<NotificationModels.InboxView> markAllRead(Authentication authentication) {
        return ApiResponse.ok(service.markAllRead(authentication));
    }
}
