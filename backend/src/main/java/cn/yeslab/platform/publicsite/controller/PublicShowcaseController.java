package cn.yeslab.platform.publicsite.controller;

import cn.yeslab.platform.common.api.ApiResponse;
import cn.yeslab.platform.publicsite.model.PublicShowcase;
import cn.yeslab.platform.publicsite.service.PublicShowcaseService;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/public")
public class PublicShowcaseController {

    private final PublicShowcaseService service;

    public PublicShowcaseController(PublicShowcaseService service) {
        this.service = service;
    }

    @GetMapping("/home")
    public ApiResponse<PublicShowcase.Home> home() {
        return ApiResponse.ok(service.getHome());
    }

    @GetMapping("/projects")
    public ApiResponse<List<PublicShowcase.Project>> projects() {
        return ApiResponse.ok(service.getProjects());
    }

    @GetMapping("/projects/{slug}")
    public ApiResponse<PublicShowcase.Project> project(
            @PathVariable @Pattern(regexp = "[a-z0-9-]+") String slug
    ) {
        return ApiResponse.ok(service.getProject(slug));
    }

    @GetMapping("/members")
    public ApiResponse<List<PublicShowcase.Member>> members() {
        return ApiResponse.ok(service.getMembers());
    }

    @GetMapping("/members/{slug}")
    public ApiResponse<PublicShowcase.Member> member(
            @PathVariable @Pattern(regexp = "[a-z0-9-]+") String slug
    ) {
        return ApiResponse.ok(service.getMember(slug));
    }

    @GetMapping("/rankings")
    public ApiResponse<List<PublicShowcase.RankingEntry>> rankings(
            @RequestParam(defaultValue = "总榜") String board
    ) {
        return ApiResponse.ok(service.getRanking(board));
    }

    @GetMapping("/updates")
    public ApiResponse<List<PublicShowcase.Update>> updates() {
        return ApiResponse.ok(service.getUpdates());
    }
}
