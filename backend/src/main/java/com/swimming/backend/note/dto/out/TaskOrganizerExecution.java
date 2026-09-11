package com.swimming.backend.note.dto.out;

public record TaskOrganizerExecution<T>(
        T output,
        LlmCallSnapshot call
) {
}
