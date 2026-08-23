package com.swimming.backend.session.dto.web;

import com.swimming.backend.task.dto.projection.TaskReference;

public record SessionTaskResponse(
        Long id,
        Long projectId,
        String projectName,
        String title
) {
    public static SessionTaskResponse from(TaskReference task) {
        return new SessionTaskResponse(
                task.id(),
                task.projectId(),
                task.projectName(),
                task.title()
        );
    }
}
