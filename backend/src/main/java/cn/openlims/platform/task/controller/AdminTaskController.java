package cn.openlims.platform.task.controller;

import cn.openlims.platform.common.api.ApiResponse;
import cn.openlims.platform.task.api.TaskModels;
import cn.openlims.platform.task.model.TaskStatus;
import cn.openlims.platform.task.service.OnboardingTaskService;
import cn.openlims.platform.task.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 任务模块管理端：新手任务大任务（所有技能测试阶段报名者共享）与总览、批量补发、人工审核，
 * 以及普通任务的创建、发布、完成情况与补充发放。全部接口受 {@code TASK_MANAGE} 保护。
 */
@RestController
@RequestMapping("/api/v1/admin/tasks")
public class AdminTaskController {

    private final TaskService service;
    private final OnboardingTaskService onboardingTaskService;

    public AdminTaskController(TaskService service, OnboardingTaskService onboardingTaskService) {
        this.service = service;
        this.onboardingTaskService = onboardingTaskService;
    }

    // ---------- 新手任务 ----------

    @GetMapping("/onboarding")
    public ApiResponse<TaskModels.OnboardingTaskAdminView> onboardingTask() {
        return ApiResponse.ok(onboardingTaskService.current());
    }

    @PutMapping("/onboarding")
    public ApiResponse<TaskModels.SaveOnboardingTaskResult> saveOnboardingTask(
            Authentication authentication,
            @Valid @RequestBody TaskModels.SaveOnboardingTaskRequest request
    ) {
        return ApiResponse.ok(onboardingTaskService.save(authentication, request));
    }

    @GetMapping("/onboarding-overview")
    public ApiResponse<TaskModels.OnboardingOverviewView> onboardingOverview() {
        return ApiResponse.ok(service.onboardingOverview());
    }

    @PostMapping("/onboarding-tasks/backfill")
    public ApiResponse<TaskModels.BackfillResult> backfill(Authentication authentication) {
        return ApiResponse.ok(service.backfillOnboardingTasks(authentication));
    }

    /**
     * 按人延长新手任务的截止日期。
     *
     * <p>到期（本人 {@code due_date} 已过）会冻结该报名者的新提交；已提交内容仍可审核，必须把截止日期
     * 推到未来才能继续；延长只影响这一位报名者，其他人不受影响。</p>
     */
    @PutMapping("/onboarding-assignments/{assignmentId}/due-date")
    public ApiResponse<TaskModels.DueDateExtensionView> extendOnboardingDueDate(
            Authentication authentication,
            @PathVariable UUID assignmentId,
            @Valid @RequestBody TaskModels.ExtendOnboardingDueDateRequest request
    ) {
        return ApiResponse.ok(onboardingTaskService.extendDueDate(authentication, assignmentId, request));
    }

    // ---------- 普通任务 ----------

    @GetMapping
    public ApiResponse<List<TaskModels.TaskSummaryView>> list(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(service.listStandardTasks(status, keyword));
    }

    @PostMapping
    public ApiResponse<TaskModels.TaskView> create(
            Authentication authentication,
            @Valid @RequestBody TaskModels.CreateTaskRequest request
    ) {
        return ApiResponse.ok(service.createStandardTask(authentication, request));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<TaskModels.TaskView> task(@PathVariable UUID taskId) {
        return ApiResponse.ok(service.standardTask(taskId));
    }

    @PutMapping("/{taskId}")
    public ApiResponse<TaskModels.TaskView> update(
            Authentication authentication,
            @PathVariable UUID taskId,
            @Valid @RequestBody TaskModels.CreateTaskRequest request
    ) {
        return ApiResponse.ok(service.updateStandardTask(authentication, taskId, request));
    }

    @GetMapping("/member-options")
    public ApiResponse<List<TaskModels.MemberOptionView>> memberOptions() {
        return ApiResponse.ok(service.memberOptions());
    }

    @PostMapping("/audience-preview")
    public ApiResponse<TaskModels.AudiencePreviewView> previewAudience(
            Authentication authentication,
            @RequestBody TaskModels.AudienceRequest request
    ) {
        return ApiResponse.ok(service.previewAudience(authentication, request));
    }

    @PostMapping("/{taskId}/publish")
    public ApiResponse<TaskModels.TaskView> publish(Authentication authentication, @PathVariable UUID taskId) {
        return ApiResponse.ok(service.publishStandardTask(authentication, taskId));
    }

    @PostMapping("/{taskId}/close")
    public ApiResponse<TaskModels.TaskView> close(@PathVariable UUID taskId) {
        return ApiResponse.ok(service.closeStandardTask(taskId));
    }

    @DeleteMapping("/{taskId}")
    public ApiResponse<Void> delete(@PathVariable UUID taskId) {
        service.deleteStandardTask(taskId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{taskId}/assignments")
    public ApiResponse<TaskModels.TaskView> supplement(
            @PathVariable UUID taskId,
            @Valid @RequestBody TaskModels.AudienceRequest request
    ) {
        return ApiResponse.ok(service.supplementAssignments(taskId, request));
    }

    @DeleteMapping("/{taskId}/assignments/{assignmentId}")
    public ApiResponse<TaskModels.TaskView> removeAssignment(
            @PathVariable UUID taskId,
            @PathVariable UUID assignmentId
    ) {
        return ApiResponse.ok(service.removeAssignment(taskId, assignmentId));
    }

    @GetMapping("/{taskId}/subtasks/{subtaskId}")
    public ApiResponse<TaskModels.SubtaskDetailView> subtask(
            @PathVariable UUID taskId,
            @PathVariable UUID subtaskId
    ) {
        return ApiResponse.ok(service.adminSubtask(taskId, subtaskId));
    }

    /** 审核时展开某人的子任务提交内容。 */
    @GetMapping("/{taskId}/assignments/{assignmentId}/subtasks")
    public ApiResponse<List<TaskModels.SubtaskSubmissionView>> assignmentSubtasks(
            @PathVariable UUID taskId,
            @PathVariable UUID assignmentId
    ) {
        return ApiResponse.ok(service.adminAssignmentSubtasks(taskId, assignmentId));
    }

    @GetMapping("/{taskId}/progress")
    public ApiResponse<TaskModels.TaskProgressView> progress(@PathVariable UUID taskId) {
        return ApiResponse.ok(service.taskProgress(taskId));
    }

    // ---------- 人工审核（新手任务转正 / 普通任务计分） ----------

    @PutMapping("/{taskId}/assignments/{assignmentId}/review")
    public ApiResponse<TaskModels.ReviewResultView> review(
            Authentication authentication,
            @PathVariable UUID taskId,
            @PathVariable UUID assignmentId,
            @Valid @RequestBody TaskModels.ReviewRequest request
    ) {
        return ApiResponse.ok(service.review(authentication, taskId, assignmentId, request));
    }
}
