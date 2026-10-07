package cn.openlims.platform.task.controller;

import cn.openlims.platform.common.api.ApiResponse;
import cn.openlims.platform.task.api.TaskModels;
import cn.openlims.platform.task.service.BountyService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 悬赏任务成员端：悬赏榜、详情、接取与放弃。
 *
 * <p>提交完成说明不在这个控制器里——它与普通任务共用 {@code POST /api/v1/tasks/{assignmentId}/submission}，
 * 由服务层按任务类型分派（悬赏是「提交即完成」）。</p>
 */
@RestController
@RequestMapping("/api/v1/bounties")
public class BountyController {

    private final BountyService service;

    public BountyController(BountyService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<TaskModels.BountyBoardItemView>> board(Authentication authentication) {
        return ApiResponse.ok(service.board(authentication));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<TaskModels.BountyDetailView> detail(Authentication authentication, @PathVariable UUID taskId) {
        return ApiResponse.ok(service.detail(authentication, taskId));
    }

    @PostMapping("/{taskId}/claim")
    public ApiResponse<TaskModels.BountyClaimResult> claim(Authentication authentication, @PathVariable UUID taskId) {
        return ApiResponse.ok(service.claim(authentication, taskId));
    }

    @PostMapping("/{taskId}/abandon")
    public ApiResponse<TaskModels.BountyAbandonResult> abandon(
            Authentication authentication,
            @PathVariable UUID taskId
    ) {
        return ApiResponse.ok(service.abandon(authentication, taskId));
    }
}
