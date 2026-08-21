package com.swimming.backend.session.dto.web;

import com.swimming.backend.task.dto.projection.TaskReference;

public record ActiveSessionTaskResponse(
        Long id,
        Long projectId,
        String projectName,
        String title
) {
    public static ActiveSessionTaskResponse from(TaskReference task) {
        return new ActiveSessionTaskResponse(
                task.id(),
                task.projectId(),
                task.projectName(),
                task.title()
        );
    }
}
