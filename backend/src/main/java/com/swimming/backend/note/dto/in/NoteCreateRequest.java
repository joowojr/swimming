package com.swimming.backend.note.dto.in;

import com.swimming.backend.note.domain.NoteContextType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NoteCreateRequest(

        @NotBlank
        String content,

        @NotNull
        NoteContextType contextType,

        Long projectId,

        Long sessionId
) {
}