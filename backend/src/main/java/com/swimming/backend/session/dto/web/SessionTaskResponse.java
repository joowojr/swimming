package com.swimming.backend.session.dto.web;

import com.swimming.backend.task.dto.projection.TaskReference;

public record SessionTaskResponse(
        Long id,
        Long projectId,
        String projectName,
        String title,
        Boolean isCompleted
) {
    public static SessionTaskResponse from(TaskReference task, Boolean isCompleted) {
        return new SessionTaskResponse(
                task.id(),
                task.projectId(),
                task.projectName(),
                task.title(),
                isCompleted
        );
    }
}
