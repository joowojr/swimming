package com.swimming.backend.note.service;

import com.swimming.backend.note.dto.out.TaskOrganizerInput;

record TaskOrganizerTestScenario(
        String id,
        String name,
        String evaluationCriteria,
        TaskOrganizerInput input
) {
}
