package com.swimming.backend.session.dto.web;

import com.swimming.backend.task.dto.projection.TaskReference;

public record SessionTaskResponse(
        Long id,
        Long folderId,
        String folderName,
        String title,
        Boolean isCompleted
) {
    public static SessionTaskResponse from(TaskReference task, Boolean isCompleted) {
        return new SessionTaskResponse(
                task.id(),
                task.folderId(),
                task.folderName(),
                task.title(),
                isCompleted
        );
    }
}
