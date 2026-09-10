package cn.yeslab.platform.notification.controller;

import cn.yeslab.platform.common.api.ApiResponse;
import cn.yeslab.platform.notification.api.NotificationModels;
import cn.yeslab.platform.notification.service.MelinaVisibilityService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/notifications")
public class AdminNotificationController {
    private final MelinaVisibilityService visibility;

    public AdminNotificationController(MelinaVisibilityService visibility) { this.visibility = visibility; }

    @GetMapping("/melina-visibility")
    public ApiResponse<NotificationModels.AdminVisibilityView> getVisibility() {
        return ApiResponse.ok(visibility.adminView());
    }

    @PutMapping("/melina-visibility")
    public ApiResponse<NotificationModels.AdminVisibilityView> updateVisibility(
            @Valid @RequestBody NotificationModels.AdminVisibilityRequest request) {
        return ApiResponse.ok(visibility.update(request));
    }
}
