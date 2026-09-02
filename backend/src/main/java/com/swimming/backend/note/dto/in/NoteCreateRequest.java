package com.swimming.backend.note.dto.in;

import com.swimming.backend.note.domain.NoteContextType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NoteCreateRequest(

        @NotBlank
        @Size(max = 1024, message = "노트 내용은 1024자 이하여야 합니다")
        String content,

        @NotNull
        NoteContextType contextType,

        Long folderId,

        Long sessionId
) {
}
