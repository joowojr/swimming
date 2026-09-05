package com.swimming.backend.task.controller;

import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.task.dto.in.CreateTaskRequest;
import com.swimming.backend.task.dto.in.CreateTaskWithPlanRequest;
import com.swimming.backend.task.dto.in.DeleteTasksRequest;
import com.swimming.backend.task.dto.in.TaskResponse;
import com.swimming.backend.task.dto.in.TaskSort;
import com.swimming.backend.task.dto.in.UpdateTaskInfoRequest;
import com.swimming.backend.task.dto.in.UpdateTaskInfoResponse;
import com.swimming.backend.task.dto.in.UpdateTaskStatusRequest;
import com.swimming.backend.task.dto.in.UpdateTaskTitleRequest;
import com.swimming.backend.task.dto.in.UpdateTaskPriorityRequest;
import com.swimming.backend.task.dto.in.UpdateTaskUrgentRequest;
import com.swimming.backend.task.usecase.TaskUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TaskController {

    private final TaskUseCase taskUseCase;

    @PostMapping("/tasks")
    public ResponseEntity<TaskResponse> createWithOptionalPlan(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody CreateTaskWithPlanRequest request
    ) {
        TaskResponse response = taskUseCase.createWithOptionalPlan(authUser.id(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/tasks/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PostMapping("/folders/{folderId}/tasks")
    @Deprecated(since = "2026-09-01", forRemoval = true)
    public ResponseEntity<TaskResponse> create(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable("folderId") Long folderId,
            @Valid @RequestBody CreateTaskRequest request
    ) {
        TaskResponse response = taskUseCase.create(authUser.id(), folderId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/tasks/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/folders/{folderId}/tasks")
    public ResponseEntity<List<TaskResponse>> getByFolder(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable("folderId") Long folderId
    ) {
        return ResponseEntity.ok(taskUseCase.getByFolder(authUser.id(), folderId));
    }

    @GetMapping("/tasks")
    public ResponseEntity<List<TaskResponse>> getList(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String sort
    ) {
        return ResponseEntity.ok(taskUseCase.getList(authUser.id(), TaskSort.fromQuery(sort)));
    }

    @PatchMapping("/tasks/{taskId}/title")
    public ResponseEntity<TaskResponse> updateTitle(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long taskId,
            @Valid @RequestBody UpdateTaskTitleRequest request
    ) {
        return ResponseEntity.ok(taskUseCase.updateTitle(authUser.id(), taskId, request));
    }

    @PatchMapping("/tasks/{taskId}/status")
    public ResponseEntity<TaskResponse> updateStatus(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long taskId,
            @Valid @RequestBody UpdateTaskStatusRequest request
    ) {
        return ResponseEntity.ok(taskUseCase.updateStatus(authUser.id(), taskId, request));
    }

    @PatchMapping("/tasks/{taskId}/info")
    public ResponseEntity<UpdateTaskInfoResponse> updateInfo(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long taskId,
            @Valid @RequestBody UpdateTaskInfoRequest request
    ) {
        return ResponseEntity.ok(taskUseCase.updateInfo(authUser.id(), taskId, request));
    }

    /** @deprecated PATCH /tasks/{taskId}/info 의 priority를 쓴다. */
    @Deprecated
    @PatchMapping("/tasks/{taskId}/priority")
    public ResponseEntity<TaskResponse> updatePriority(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long taskId,
            @RequestBody UpdateTaskPriorityRequest request
    ) {
        return ResponseEntity.ok(taskUseCase.updatePriority(authUser.id(), taskId, request));
    }

    /** @deprecated PATCH /tasks/{taskId}/info 의 urgent를 쓴다. */
    @Deprecated
    @PatchMapping("/tasks/{taskId}/urgent")
    public ResponseEntity<TaskResponse> updateUrgent(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long taskId,
            @RequestBody UpdateTaskUrgentRequest request
    ) {
        return ResponseEntity.ok(taskUseCase.updateUrgent(authUser.id(), taskId, request));
    }

    @DeleteMapping("/tasks")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody DeleteTasksRequest request
    ) {
        taskUseCase.deleteTasks(authUser.id(), request);
        return ResponseEntity.noContent().build();
    }

}
