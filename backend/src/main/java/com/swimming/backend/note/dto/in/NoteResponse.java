package com.swimming.backend.note.dto.in;

import com.swimming.backend.note.domain.NoteContextType;
import com.swimming.backend.note.domain.NoteStatus;

import java.time.LocalDateTime;

public record NoteResponse(
        Long id,
        String content,
        NoteStatus status,
        NoteContextType contextType,
        Long projectId,
        Long sessionId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}