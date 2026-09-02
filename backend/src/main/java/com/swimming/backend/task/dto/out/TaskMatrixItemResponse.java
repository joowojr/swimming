package com.swimming.backend.task.dto.out;

import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.domain.TaskStatus;

import java.time.Instant;

public record TaskMatrixItemResponse(
        Long id,
        Long folderId,
        String title,
        TaskStatus status,
        boolean priority,
        boolean urgent,
        String positionCursor,
        Instant createdAt,
        Instant updatedAt
) {
    public static TaskMatrixItemResponse from(Task task, String positionCursor) {
        return new TaskMatrixItemResponse(
                task.getId(),
                task.getFolderId(),
                task.getTitle(),
                task.getStatus(),
                task.isPriority(),
                task.isUrgent(),
                positionCursor,
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
