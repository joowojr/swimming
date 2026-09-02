package com.swimming.backend.note.dto.out;

import com.swimming.backend.task.domain.TaskStatus;

public record TaskContext(
        Long id,
        Long folderId,
        String title,
        TaskStatus status
) {
}