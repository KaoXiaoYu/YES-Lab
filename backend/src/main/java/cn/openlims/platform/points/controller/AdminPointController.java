package cn.openlims.platform.points.controller;

import cn.openlims.platform.common.api.ApiResponse;
import cn.openlims.platform.points.api.PointModels;
import cn.openlims.platform.points.service.PointService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/points")
public class AdminPointController {

    private final PointService service;

    public AdminPointController(PointService service) {
        this.service = service;
    }

    @PostMapping("/grants")
    public ApiResponse<PointModels.GrantView> grant(
            Authentication authentication,
            @Valid @RequestBody PointModels.ManualGrantRequest request
    ) {
        return ApiResponse.ok(service.grant(authentication, request));
    }

    @GetMapping("/sources")
    public ApiResponse<PointModels.SourceOptions> sources(Authentication authentication) {
        return ApiResponse.ok(service.sources(authentication));
    }

    @PostMapping("/grants/{grantId}/reversal")
    public ApiResponse<PointModels.GrantView> reverse(
            Authentication authentication,
            @PathVariable UUID grantId,
            @Valid @RequestBody PointModels.ReversalRequest request
    ) {
        return ApiResponse.ok(service.reverse(authentication, grantId, request));
    }

    @GetMapping("/grants")
    public ApiResponse<List<PointModels.GrantView>> grants() {
        return ApiResponse.ok(service.recentGrants());
    }
}
