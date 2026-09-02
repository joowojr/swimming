package com.swimming.backend.note.dto.in;

import java.util.List;

public record TaskOrganizeConfirmResponse(
        List<CreatedTaskResponse> createdTasks
) {

    public record CreatedTaskResponse(
            Long id,
            Long folderId,
            String title
    ) {
    }
}
