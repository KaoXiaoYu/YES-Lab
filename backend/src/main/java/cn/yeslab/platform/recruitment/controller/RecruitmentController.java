package cn.yeslab.platform.recruitment.controller;

import cn.yeslab.platform.common.api.ApiResponse;
import cn.yeslab.platform.recruitment.api.RecruitmentModels;
import cn.yeslab.platform.recruitment.service.RecruitmentService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recruitment")
public class RecruitmentController {

    private final RecruitmentService service;

    public RecruitmentController(RecruitmentService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public ApiResponse<RecruitmentModels.ApplicationView> mine(Authentication authentication) {
        return ApiResponse.ok(service.getOwn(authentication));
    }

    @GetMapping("/me/questions")
    public ApiResponse<List<RecruitmentModels.TechnicalQuestionView>> questions(Authentication authentication) {
        return ApiResponse.ok(service.getOwnQuestions(authentication));
    }

    @PutMapping("/me")
    public ApiResponse<RecruitmentModels.ApplicationView> save(
            Authentication authentication,
            @Valid @RequestBody RecruitmentModels.ApplicationRequest request
    ) {
        return ApiResponse.ok(service.saveOwn(authentication, request));
    }

    @PostMapping(value = "/me/portfolio-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RecruitmentModels.ApplicationView> uploadPortfolio(Authentication authentication,
            @RequestPart("images") List<MultipartFile> images) {
        return ApiResponse.ok(service.uploadOwnPortfolio(authentication, images));
    }

    @DeleteMapping("/me/portfolio-images/{imageId}")
    public ApiResponse<RecruitmentModels.ApplicationView> deletePortfolio(Authentication authentication, @PathVariable UUID imageId) {
        return ApiResponse.ok(service.deleteOwnPortfolioImage(authentication, imageId));
    }

    @GetMapping("/applications/{applicationId}/portfolio-images/{imageId}")
    public ResponseEntity<Resource> portfolioImage(Authentication authentication, @PathVariable UUID applicationId,
            @PathVariable UUID imageId) {
        RecruitmentService.FileDownload file = service.portfolioImage(authentication, applicationId, imageId);
        MediaType type;
        try { type = MediaType.parseMediaType(file.contentType()); } catch (Exception ignored) { type = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(file.originalName(), StandardCharsets.UTF_8).build().toString())
                .body(file.resource());
    }
}
