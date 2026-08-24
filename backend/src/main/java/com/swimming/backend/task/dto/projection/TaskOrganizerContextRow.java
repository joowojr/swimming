package com.swimming.backend.task.dto.projection;

import com.swimming.backend.task.domain.TaskStatus;

public record TaskOrganizerContextRow(
        Long projectId,
        String projectName,
        String projectDescription,
        Long taskId,
        String taskTitle,
        TaskStatus taskStatus
) {
}
