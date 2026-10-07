package cn.yeslab.platform.administration;

import cn.yeslab.platform.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin landing page: what is waiting for someone, across modules, in one request. */
@RestController
@RequestMapping("/api/v1/admin/overview")
public class AdminOverviewController {
    private final AdminOverviewService service;

    public AdminOverviewController(AdminOverviewService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<AdminOverviewService.OverviewView> overview() {
        return ApiResponse.ok(service.overview());
    }
}
