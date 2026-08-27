package cn.yeslab.platform.achievement.controller;

import cn.yeslab.platform.achievement.api.AchievementModels;
import cn.yeslab.platform.achievement.service.AchievementService;
import cn.yeslab.platform.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/competitions")
public class CompetitionController {
    private final AchievementService service;
    public CompetitionController(AchievementService service) { this.service = service; }

    @GetMapping public ApiResponse<List<AchievementModels.CompetitionView>> list(Authentication authentication) { return ApiResponse.ok(service.listCompetitions(authentication)); }
    @GetMapping("/countdown") public ApiResponse<AchievementModels.CompetitionCountdownView> countdown(Authentication authentication) { return ApiResponse.ok(service.ownUpcomingCompetition(authentication)); }
    @GetMapping("/member-options") public ApiResponse<List<AchievementModels.MemberOption>> memberOptions() { return ApiResponse.ok(service.memberOptions()); }
    @GetMapping("/project-options") public ApiResponse<List<AchievementModels.ProjectOption>> projectOptions(Authentication authentication) { return ApiResponse.ok(service.projectOptions(authentication)); }
    @GetMapping("/{id}") public ApiResponse<AchievementModels.CompetitionView> get(Authentication authentication, @PathVariable UUID id) { return ApiResponse.ok(service.getCompetition(authentication, id)); }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AchievementModels.CompetitionView> create(Authentication authentication,
            @Valid @RequestPart("data") AchievementModels.CompetitionUpsertRequest data,
            @RequestPart(value = "certificate", required = false) MultipartFile certificate,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        return ApiResponse.ok(service.createCompetition(authentication, data, certificate, images));
    }

    @PutMapping("/{id}")
    public ApiResponse<AchievementModels.CompetitionView> update(Authentication authentication, @PathVariable UUID id,
            @Valid @RequestBody AchievementModels.CompetitionUpsertRequest data) {
        return ApiResponse.ok(service.updateCompetition(authentication, id, data));
    }

    @PutMapping(value = "/{id}/certificate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AchievementModels.CompetitionView> certificate(Authentication authentication, @PathVariable UUID id,
            @RequestPart("certificate") MultipartFile certificate) {
        return ApiResponse.ok(service.replaceCertificate(authentication, id, certificate));
    }

    @PutMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AchievementModels.CompetitionView> images(Authentication authentication, @PathVariable UUID id,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @RequestParam(value = "descriptions", required = false) List<String> descriptions) {
        return ApiResponse.ok(service.replaceImages(authentication, id, images, descriptions));
    }

    @GetMapping("/{id}/certificate")
    public ResponseEntity<Resource> certificateFile(Authentication authentication, @PathVariable UUID id) {
        return file(service.certificate(authentication, id));
    }

    @GetMapping("/{competitionId}/images/{imageId}")
    public ResponseEntity<Resource> image(Authentication authentication, @PathVariable UUID competitionId, @PathVariable UUID imageId) {
        return file(service.internalImage(authentication, competitionId, imageId));
    }

    @DeleteMapping("/{competitionId}/images/{imageId}")
    public ApiResponse<AchievementModels.CompetitionView> deleteImage(Authentication authentication,
            @PathVariable UUID competitionId, @PathVariable UUID imageId) {
        return ApiResponse.ok(service.deleteImage(authentication, competitionId, imageId));
    }

    static ResponseEntity<Resource> file(AchievementService.FileDownload file) {
        return file(file, null);
    }

    static ResponseEntity<Resource> publicFile(AchievementService.FileDownload file) {
        return file(file, CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }

    private static ResponseEntity<Resource> file(AchievementService.FileDownload file, CacheControl cacheControl) {
        MediaType contentType;
        try { contentType = MediaType.parseMediaType(file.contentType()); } catch (Exception ignored) { contentType = MediaType.APPLICATION_OCTET_STREAM; }
        ResponseEntity.BodyBuilder response = ResponseEntity.ok().contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(file.originalName(), StandardCharsets.UTF_8).build().toString());
        if (cacheControl != null) response.cacheControl(cacheControl);
        return response.body(file.resource());
    }
}
