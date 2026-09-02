package com.swimming.backend.note.dto.in;

import com.swimming.backend.note.domain.NoteContextType;
import com.swimming.backend.note.domain.NoteStatus;

import java.time.Instant;

public record NoteResponse(
        Long id,
        String content,
        NoteStatus status,
        NoteContextType contextType,
        Long folderId,
        Long sessionId,
        Instant createdAt,
        Instant updatedAt
) {
}