package com.swimming.backend.task.dto;

import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.domain.TaskStatus;

import java.time.LocalDateTime;

public record TaskResponse(
        Long id,
        Long projectId,
        String title,
        TaskStatus status,
        int completionPct,
        int orderIdx,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getProjectId(),
                task.getTitle(),
                task.getStatus(),
                task.getCompletionPct(),
                task.getOrderIdx(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
