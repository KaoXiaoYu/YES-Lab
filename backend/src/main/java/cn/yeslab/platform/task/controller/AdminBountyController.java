package cn.yeslab.platform.task.controller;

import cn.yeslab.platform.common.api.ApiResponse;
import cn.yeslab.platform.task.api.TaskModels;
import cn.yeslab.platform.task.service.BountyService;
import cn.yeslab.platform.task.service.TaskSettlementService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 悬赏任务管理端（{@code TASK_MANAGE}）：创建、发布、结束、接取名单与事后复核。
 *
 * <p>与普通任务分开一组路径：悬赏的对象由成员自主接取产生，不参与「按等级条件发放」那套流程。
 * 没有「手动结算」接口——积分只由定时任务与「结束悬赏」触发结算。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/bounties")
public class AdminBountyController {

    private final BountyService service;
    private final TaskSettlementService settlement;

    public AdminBountyController(BountyService service, TaskSettlementService settlement) {
        this.service = service;
        this.settlement = settlement;
    }

    @GetMapping
    public ApiResponse<List<TaskModels.BountySummaryView>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    public ApiResponse<TaskModels.TaskView> create(
            Authentication authentication,
            @Valid @RequestBody TaskModels.CreateBountyRequest request
    ) {
        return ApiResponse.ok(service.create(authentication, request));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<TaskModels.TaskView> task(@PathVariable UUID taskId) {
        return ApiResponse.ok(service.claims(taskId).task());
    }

    @PutMapping("/{taskId}")
    public ApiResponse<TaskModels.TaskView> update(
            @PathVariable UUID taskId,
            @Valid @RequestBody TaskModels.CreateBountyRequest request
    ) {
        return ApiResponse.ok(service.update(taskId, request));
    }

    @PostMapping("/{taskId}/publish")
    public ApiResponse<TaskModels.TaskView> publish(@PathVariable UUID taskId) {
        return ApiResponse.ok(service.publish(taskId));
    }

    /** 结束悬赏：成员侧只读，并**同步触发一次积分结算**（结束 = 到期 = 结算 = 终局）。 */
    @PostMapping("/{taskId}/close")
    public ApiResponse<TaskModels.TaskView> close(@PathVariable UUID taskId) {
        TaskModels.TaskView closed = service.close(taskId);
        settlement.settle(taskId);
        return ApiResponse.ok(closed);
    }

    @DeleteMapping("/{taskId}")
    public ApiResponse<Void> delete(@PathVariable UUID taskId) {
        service.deleteDraft(taskId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{taskId}/claims")
    public ApiResponse<TaskModels.BountyClaimsView> claims(@PathVariable UUID taskId) {
        return ApiResponse.ok(service.claims(taskId));
    }

    /** 事后驳回：已完成 -> 已驳回，归还名额并触发奖金顺延；到期后禁止。 */
    @PostMapping("/{taskId}/claims/{assignmentId}/revoke")
    public ApiResponse<TaskModels.BountyClaimRowView> revoke(
            Authentication authentication,
            @PathVariable UUID taskId,
            @PathVariable UUID assignmentId,
            @Valid @RequestBody TaskModels.BountyRevokeRequest request
    ) {
        return ApiResponse.ok(service.revoke(authentication, taskId, assignmentId, request.comment()));
    }

    /** 登记管理员已在线下实际发放奖金。允许任务到期或关闭后补录。 */
    @PostMapping("/{taskId}/claims/{assignmentId}/prize-fulfillment/issue")
    public ApiResponse<TaskModels.BountyClaimRowView> issuePrize(
            Authentication authentication,
            @PathVariable UUID taskId,
            @PathVariable UUID assignmentId
    ) {
        return ApiResponse.ok(service.issuePrize(authentication, taskId, assignmentId));
    }

    /** 移除接取者：进行中 -> 已放弃，归还名额；到期后仍可执行（清理动作）。 */
    @DeleteMapping("/{taskId}/claims/{assignmentId}")
    public ApiResponse<TaskModels.BountyClaimsView> removeClaim(
            @PathVariable UUID taskId,
            @PathVariable UUID assignmentId
    ) {
        return ApiResponse.ok(service.removeClaim(taskId, assignmentId));
    }
}
