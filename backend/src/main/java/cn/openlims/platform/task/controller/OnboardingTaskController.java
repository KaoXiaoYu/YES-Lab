package cn.openlims.platform.task.controller;

import cn.openlims.platform.common.api.ApiResponse;
import cn.openlims.platform.task.api.TaskModels;
import cn.openlims.platform.task.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 报名者端新手任务。路径挂在招新下，沿用 RECRUITMENT_SELF_VIEW / RECRUITMENT_SELF_EDIT，
 * 因为报名者是 VISITOR，访问不了成员端的 /api/v1/tasks。
 */
@RestController
@RequestMapping("/api/v1/recruitment/me/onboarding-task")
public class OnboardingTaskController {

    private final TaskService service;

    public OnboardingTaskController(TaskService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<TaskModels.OnboardingTaskView> mine(Authentication authentication) {
        return ApiResponse.ok(service.ownOnboardingTask(authentication));
    }

    @GetMapping("/subtasks/{subtaskId}")
    public ApiResponse<TaskModels.SubtaskDetailView> subtask(
            Authentication authentication,
            @PathVariable UUID subtaskId
    ) {
        return ApiResponse.ok(service.ownOnboardingSubtask(authentication, subtaskId));
    }

    @PostMapping("/subtasks/{subtaskId}/submission")
    public ApiResponse<TaskModels.OnboardingTaskView> submitSubtask(
            Authentication authentication,
            @PathVariable UUID subtaskId,
            @Valid @RequestBody TaskModels.SubtaskSubmissionRequest request
    ) {
        return ApiResponse.ok(service.submitOwnSubtask(authentication, subtaskId, request.contentHtml()));
    }

    @PostMapping("/submission")
    public ApiResponse<TaskModels.OnboardingTaskView> submit(
            Authentication authentication,
            @Valid @RequestBody TaskModels.SubmitTaskRequest request
    ) {
        return ApiResponse.ok(service.submitOwnOnboarding(authentication, request.completionNote()));
    }
}
