package com.swimming.backend.task.dto.in;

import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.domain.TaskStatus;

import java.time.LocalDateTime;

public record TaskResponse(
        Long id,
        Long projectId,
        String title,
        TaskStatus status,
        boolean priority,
        boolean urgent,
        int orderIdx,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public TaskResponse(
            Long id,
            Long projectId,
            String title,
            TaskStatus status,
            int orderIdx,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this(id, projectId, title, status, false, false, orderIdx, createdAt, updatedAt);
    }

    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getProjectId(),
                task.getTitle(),
                task.getStatus(),
                task.isPriority(),
                task.isUrgent(),
                task.getOrderIdx(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
