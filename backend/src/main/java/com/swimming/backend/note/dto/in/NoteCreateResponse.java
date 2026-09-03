package com.swimming.backend.note.dto.in;

import com.swimming.backend.note.domain.Note;
import com.swimming.backend.note.domain.NoteContextType;
import com.swimming.backend.note.domain.NoteStatus;

import java.time.Instant;

public record NoteCreateResponse(
        Long id,
        String content,
        NoteStatus status,
        NoteContextType contextType,
        Long folderId,
        Long sessionId,
        Instant createdAt,
        Instant updatedAt
) {
    public static NoteCreateResponse from(Note note) {
        return new NoteCreateResponse(
                note.getId(),
                note.getContent(),
                note.getStatus(),
                note.getContextType(),
                note.getFolderId(),
                note.getSessionId(),
                note.getCreatedAt(),
                note.getUpdatedAt()
        );
    }
}
