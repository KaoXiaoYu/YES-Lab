package cn.openlims.platform.project.controller;

import cn.openlims.platform.common.api.ApiResponse;
import cn.openlims.platform.project.api.ProjectModels;
import cn.openlims.platform.project.service.ProjectTeamService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/project-teams")
public class PublicProjectTeamController {

    private final ProjectTeamService service;

    public PublicProjectTeamController(ProjectTeamService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ProjectModels.PublicProjectView>> projects() {
        return ApiResponse.ok(service.listPublicProjects());
    }

    @GetMapping("/{projectId}")
    public ApiResponse<ProjectModels.PublicProjectView> project(@PathVariable UUID projectId) {
        return ApiResponse.ok(service.getPublicProject(projectId));
    }

    @GetMapping("/{projectId}/cover")
    public ResponseEntity<Resource> cover(@PathVariable UUID projectId) {
        return ProjectTeamController.publicCoverResponse(service.publicCover(projectId));
    }
}
