package com.swimming.backend.note.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NoteUpdateRequest(

        @NotBlank
        @Size(max = 1024, message = "노트 내용은 1024자 이하여야 합니다")
        String content
) {
}
