package cn.openlims.platform.publicsite.cms.controller;

import cn.openlims.platform.common.api.ApiResponse;
import cn.openlims.platform.publicsite.cms.api.HomepageModels;
import cn.openlims.platform.publicsite.cms.service.HomepageContentService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/homepage")
public class AdminHomepageController {
    private final HomepageContentService service;

    public AdminHomepageController(HomepageContentService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<HomepageModels.HomepageAdminView> content() {
        return ApiResponse.ok(service.adminContent());
    }

    @PutMapping
    public ApiResponse<HomepageModels.HomepageAdminView> update(
            Authentication authentication,
            @Valid @RequestBody HomepageModels.HomepageContent content
    ) {
        return ApiResponse.ok(service.update(authentication, content));
    }
}
