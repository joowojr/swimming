package com.swimming.backend.task.dto.projection;

import com.swimming.backend.task.domain.TaskStatus;

public record TaskReference(
        Long id,
        Long projectId,
        String projectName,
        String title,
        TaskStatus status,
        int completionPct
) {
}
