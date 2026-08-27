package com.swimming.backend.task.controller;

import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.task.dto.in.CreateTaskRequest;
import com.swimming.backend.task.dto.in.DeleteTasksRequest;
import com.swimming.backend.task.dto.in.TaskResponse;
import com.swimming.backend.task.dto.in.UpdateTaskStatusRequest;
import com.swimming.backend.task.dto.in.UpdateTaskTitleRequest;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TaskController {

    private final TaskUseCase taskUseCase;

    @PostMapping("/projects/{projectId}/tasks")
    public ResponseEntity<TaskResponse> create(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long projectId,
            @Valid @RequestBody CreateTaskRequest request
    ) {
        TaskResponse response = taskUseCase.create(authUser.id(), projectId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/tasks/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/projects/{projectId}/tasks")
    public ResponseEntity<List<TaskResponse>> getByProject(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long projectId
    ) {
        return ResponseEntity.ok(taskUseCase.getByProject(authUser.id(), projectId));
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

    @DeleteMapping("/tasks")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody DeleteTasksRequest request
    ) {
        taskUseCase.deleteTasks(authUser.id(), request);
        return ResponseEntity.noContent().build();
    }

}
