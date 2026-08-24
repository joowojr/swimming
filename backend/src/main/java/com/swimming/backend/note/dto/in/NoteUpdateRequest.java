package com.swimming.backend.note.dto.in;

import jakarta.validation.constraints.NotBlank;

public record NoteUpdateRequest(

        @NotBlank
        String content
) {
}