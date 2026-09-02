package com.swimming.backend.task.dto.in;

import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.domain.TaskStatus;

import java.time.Instant;

public record TaskResponse(
        Long id,
        Long folderId,
        String title,
        TaskStatus status,
        boolean priority,
        boolean urgent,
        int orderIdx,
        Instant createdAt,
        Instant updatedAt
) {
    public TaskResponse(
            Long id,
            Long folderId,
            String title,
            TaskStatus status,
            int orderIdx,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, folderId, title, status, false, false, orderIdx, createdAt, updatedAt);
    }

    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getFolderId(),
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
