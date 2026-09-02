package com.swimming.backend.session.dto.web;

import com.swimming.backend.task.dto.projection.TaskReference;

public record SessionTaskResponse(
        Long id,
        Long folderId,
        String projectName,
        String title,
        Boolean isCompleted
) {
    public static SessionTaskResponse from(TaskReference task, Boolean isCompleted) {
        return new SessionTaskResponse(
                task.id(),
                task.folderId(),
                task.projectName(),
                task.title(),
                isCompleted
        );
    }
}
