package com.swimming.backend.note.dto.out;

import java.util.List;
import java.time.LocalDate;

public record TaskOrganizerInput(
        String memo,
        LocalDate currentDate,
        List<FolderContext> folders,
        List<TaskContext> tasks
) {
    public TaskOrganizerInput(
            String memo,
            List<FolderContext> folders,
            List<TaskContext> tasks
    ) {
        this(memo, null, folders, tasks);
    }
}
