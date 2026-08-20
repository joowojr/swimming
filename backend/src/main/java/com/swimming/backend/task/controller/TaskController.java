package com.swimming.backend.task.controller;

import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.task.dto.CreateTaskRequest;
import com.swimming.backend.task.dto.ReorderTasksRequest;
import com.swimming.backend.task.dto.TaskResponse;
import com.swimming.backend.task.dto.UpdateTaskRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
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
    public ResponseEntity<List<TaskResponse>> getAll(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long projectId
    ) {
        return ResponseEntity.ok(taskUseCase.getAll(authUser.id(), projectId));
    }

    @PatchMapping("/tasks/{taskId}")
    public ResponseEntity<TaskResponse> update(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long taskId,
            @Valid @RequestBody UpdateTaskRequest request
    ) {
        return ResponseEntity.ok(taskUseCase.update(authUser.id(), taskId, request));
    }

    @DeleteMapping("/tasks/{taskId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long taskId
    ) {
        taskUseCase.delete(authUser.id(), taskId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/projects/{projectId}/tasks/order")
    public ResponseEntity<Void> reorder(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long projectId,
            @Valid @RequestBody ReorderTasksRequest request
    ) {
        taskUseCase.reorder(authUser.id(), projectId, request);
        return ResponseEntity.noContent().build();
    }
}
