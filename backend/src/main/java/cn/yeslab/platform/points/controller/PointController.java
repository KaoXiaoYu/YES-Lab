package cn.yeslab.platform.points.controller;

import cn.yeslab.platform.common.api.ApiResponse;
import cn.yeslab.platform.points.api.PointModels;
import cn.yeslab.platform.points.service.PointService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class PointController {

    private final PointService service;

    public PointController(PointService service) {
        this.service = service;
    }

    @GetMapping("/member/points")
    public ApiResponse<PointModels.MemberSummary> ownPoints(Authentication authentication) {
        return ApiResponse.ok(service.ownSummary(authentication));
    }

    @GetMapping("/points/rules")
    public ApiResponse<List<PointModels.RuleView>> rules() {
        return ApiResponse.ok(service.rules());
    }
}
