package com.swimming.backend.note.dto.in;

import com.swimming.backend.task.domain.TaskStatus;

import java.util.List;

public record TaskOrganizeConfirmResponse(
        List<CreatedTaskResponse> createdTasks
) {

    public record CreatedTaskResponse(
            Long id,
            Long folderId,
            String title,
            TaskStatus status,
            boolean priority,
            boolean urgent
    ) {
    }
}
