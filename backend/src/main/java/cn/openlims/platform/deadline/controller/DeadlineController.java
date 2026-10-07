package cn.openlims.platform.deadline.controller;
import cn.openlims.platform.common.api.ApiResponse;
import cn.openlims.platform.deadline.api.DeadlineModels.*;
import cn.openlims.platform.deadline.service.DeadlineService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1")
public class DeadlineController {
    private final DeadlineService service;
    public DeadlineController(DeadlineService service) { this.service = service; }
    @GetMapping("/public/deadlines") public ApiResponse<DeadlinePage> publicDeadlines(@RequestParam(required=false) SourceType type,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="3") int pageSize, @RequestParam(defaultValue="false") boolean homepageWindow) {
        return ApiResponse.ok(service.publicDeadlines(type, page, pageSize, homepageWindow));
    }
    @GetMapping("/me/deadlines") public ApiResponse<DeadlinePage> ownDeadlines(Authentication authentication,
            @RequestParam(required=false) SourceType type, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="3") int pageSize) {
        return ApiResponse.ok(service.ownDeadlines(authentication, type, page, pageSize));
    }
}
