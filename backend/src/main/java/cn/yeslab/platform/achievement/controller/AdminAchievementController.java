package cn.yeslab.platform.achievement.controller;

import cn.yeslab.platform.achievement.api.AchievementModels;
import cn.yeslab.platform.achievement.service.AchievementService;
import cn.yeslab.platform.common.api.ApiResponse;
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
@RequestMapping("/api/v1/admin/achievements")
public class AdminAchievementController {
    private final AchievementService service;
    public AdminAchievementController(AchievementService service) { this.service = service; }

    @PatchMapping("/competitions/{id}/review")
    public ApiResponse<AchievementModels.CompetitionView> review(Authentication authentication, @PathVariable UUID id,
            @Valid @RequestBody AchievementModels.ReviewRequest request) {
        return ApiResponse.ok(service.review(authentication, id, request));
    }
    @PatchMapping("/competitions/{id}/display")
    public ApiResponse<AchievementModels.CompetitionView> display(Authentication authentication, @PathVariable UUID id,
            @Valid @RequestBody AchievementModels.DisplayRequest request) {
        return ApiResponse.ok(service.updateDisplay(authentication, id, request));
    }
    @GetMapping("/news") public ApiResponse<List<AchievementModels.NewsView>> news() { return ApiResponse.ok(service.managedNews()); }
    @PostMapping("/news") public ApiResponse<AchievementModels.NewsView> createNews(Authentication authentication, @Valid @RequestBody AchievementModels.NewsRequest request) { return ApiResponse.ok(service.createNews(authentication, request)); }
    @PutMapping("/news/{id}") public ApiResponse<AchievementModels.NewsView> updateNews(@PathVariable UUID id, @Valid @RequestBody AchievementModels.NewsRequest request) { return ApiResponse.ok(service.updateNews(id, request)); }
}
