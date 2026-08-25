package com.swimming.backend.note.dto.out;

import java.util.List;

public record TaskOrganizerInput(
        String memo,
        List<ProjectContext> projects,
        List<TaskContext> tasks
) {
}