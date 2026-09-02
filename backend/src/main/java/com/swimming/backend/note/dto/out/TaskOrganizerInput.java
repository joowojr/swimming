package com.swimming.backend.note.dto.out;

import java.util.List;

public record TaskOrganizerInput(
        String memo,
        List<FolderContext> folders,
        List<TaskContext> tasks
) {
}