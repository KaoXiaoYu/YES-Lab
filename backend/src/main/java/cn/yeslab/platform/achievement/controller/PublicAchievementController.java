package cn.yeslab.platform.achievement.controller;

import cn.yeslab.platform.achievement.api.AchievementModels;
import cn.yeslab.platform.achievement.service.AchievementService;
import cn.yeslab.platform.common.api.ApiResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public")
public class PublicAchievementController {
    private final AchievementService service;
    public PublicAchievementController(AchievementService service) { this.service = service; }
    @GetMapping("/competitions") public ApiResponse<List<AchievementModels.PublicCompetitionView>> competitions() { return ApiResponse.ok(service.publicCompetitions()); }
    @GetMapping("/competitions/{id}") public ApiResponse<AchievementModels.PublicCompetitionView> competition(@PathVariable UUID id) { return ApiResponse.ok(service.publicCompetition(id)); }
    @GetMapping("/competitions/{competitionId}/images/{imageId}") public ResponseEntity<Resource> image(@PathVariable UUID competitionId, @PathVariable UUID imageId) { return CompetitionController.file(service.publicImage(competitionId, imageId)); }
    @GetMapping("/news") public ApiResponse<List<AchievementModels.NewsView>> news() { return ApiResponse.ok(service.publicNews()); }
}
