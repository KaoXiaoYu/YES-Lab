package cn.yeslab.platform.project.controller;

import cn.yeslab.platform.common.api.ApiResponse;
import cn.yeslab.platform.project.api.ProjectModels;
import cn.yeslab.platform.project.service.ProjectTeamService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectTeamController {

    private final ProjectTeamService service;

    public ProjectTeamController(ProjectTeamService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ProjectModels.ProjectView>> projects(Authentication authentication) {
        return ApiResponse.ok(service.listProjects(authentication));
    }

    @GetMapping("/member-options")
    public ApiResponse<List<ProjectModels.MemberSummary>> memberOptions() {
        return ApiResponse.ok(service.memberOptions());
    }

    @PostMapping
    public ApiResponse<ProjectModels.ProjectView> create(
            Authentication authentication,
            @Valid @RequestBody ProjectModels.CreateProjectRequest request
    ) {
        return ApiResponse.ok(service.createProject(authentication, request));
    }

    @GetMapping("/{projectId}")
    public ApiResponse<ProjectModels.ProjectView> project(
            Authentication authentication,
            @PathVariable UUID projectId
    ) {
        return ApiResponse.ok(service.getProject(authentication, projectId));
    }

    @PutMapping("/{projectId}")
    public ApiResponse<ProjectModels.ProjectView> update(
            Authentication authentication,
            @PathVariable UUID projectId,
            @Valid @RequestBody ProjectModels.UpdateProjectRequest request
    ) {
        return ApiResponse.ok(service.updateProject(authentication, projectId, request));
    }

    @PutMapping("/{projectId}/team")
    public ApiResponse<ProjectModels.ProjectView> updateTeam(
            Authentication authentication,
            @PathVariable UUID projectId,
            @Valid @RequestBody ProjectModels.UpdateTeamRequest request
    ) {
        return ApiResponse.ok(service.updateTeam(authentication, projectId, request));
    }

    @PutMapping(value = "/{projectId}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ProjectModels.ProjectView> replaceCover(
            Authentication authentication,
            @PathVariable UUID projectId,
            @RequestPart("cover") MultipartFile cover
    ) {
        return ApiResponse.ok(service.replaceCover(authentication, projectId, cover));
    }

    @GetMapping("/{projectId}/cover")
    public ResponseEntity<Resource> cover(Authentication authentication, @PathVariable UUID projectId) {
        return coverResponse(service.internalCover(authentication, projectId));
    }

    static ResponseEntity<Resource> coverResponse(ProjectTeamService.CoverDownload cover) {
        return coverResponse(cover, false);
    }

    static ResponseEntity<Resource> publicCoverResponse(ProjectTeamService.CoverDownload cover) {
        return coverResponse(cover, true);
    }

    private static ResponseEntity<Resource> coverResponse(ProjectTeamService.CoverDownload cover, boolean publicResource) {
        MediaType contentType;
        try {
            contentType = MediaType.parseMediaType(cover.contentType());
        } catch (Exception ignored) {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.ok().contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(cover.originalName(), StandardCharsets.UTF_8).build().toString());
        if (publicResource) response.cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        return response.body(cover.resource());
    }
}
