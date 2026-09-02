package com.swimming.backend.task.dto.projection;

import com.swimming.backend.task.domain.TaskStatus;

public record TaskReference(
        Long id,
        Long folderId,
        String folderName,
        String title,
        TaskStatus status
) {
}
