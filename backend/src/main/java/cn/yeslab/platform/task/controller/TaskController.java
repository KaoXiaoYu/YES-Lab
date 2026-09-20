package cn.yeslab.platform.task.controller;

import cn.yeslab.platform.common.api.ApiResponse;
import cn.yeslab.platform.task.api.TaskModels;
import cn.yeslab.platform.task.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** 成员端「我的任务」（仅普通任务，新手任务走招新路径）。 */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<TaskModels.MyTaskView>> mine(Authentication authentication) {
        return ApiResponse.ok(service.myTasks(authentication));
    }

    @GetMapping("/{assignmentId}")
    public ApiResponse<TaskModels.MyTaskDetailView> detail(
            Authentication authentication,
            @PathVariable UUID assignmentId
    ) {
        return ApiResponse.ok(service.myTaskDetail(authentication, assignmentId));
    }

    @GetMapping("/{assignmentId}/subtasks/{subtaskId}")
    public ApiResponse<TaskModels.SubtaskDetailView> subtask(
            Authentication authentication,
            @PathVariable UUID assignmentId,
            @PathVariable UUID subtaskId
    ) {
        return ApiResponse.ok(service.mySubtaskDetail(authentication, assignmentId, subtaskId));
    }

    @PatchMapping("/{assignmentId}/subtasks/{subtaskId}")
    public ApiResponse<TaskModels.MyTaskDetailView> toggleSubtask(
            Authentication authentication,
            @PathVariable UUID assignmentId,
            @PathVariable UUID subtaskId,
            @Valid @RequestBody TaskModels.SubtaskToggleRequest request
    ) {
        return ApiResponse.ok(service.toggleMySubtask(authentication, assignmentId, subtaskId, request.completed()));
    }

    @PostMapping("/{assignmentId}/submission")
    public ApiResponse<TaskModels.MyTaskDetailView> submit(
            Authentication authentication,
            @PathVariable UUID assignmentId,
            @Valid @RequestBody TaskModels.SubmitTaskRequest request
    ) {
        return ApiResponse.ok(service.submitMyTask(authentication, assignmentId, request.completionNote()));
    }
}
