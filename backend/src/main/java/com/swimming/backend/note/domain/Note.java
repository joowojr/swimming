package com.swimming.backend.note.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
public class Note {

    private final Long id;
    private final Long userId;

    private String content;
    private NoteStatus status;
    private boolean deleted;

    private final NoteContextType contextType;
    private final Long folderId;
    private final Long sessionId;

    private final Instant createdAt;
    private final Instant updatedAt;

    @Builder
    public Note(
            Long id,
            Long userId,
            String content,
            NoteStatus status,
            boolean deleted,
            NoteContextType contextType,
            Long folderId,
            Long sessionId,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.content = content;
        this.status = status;
        this.deleted = deleted;
        this.contextType = contextType;
        this.folderId = folderId;
        this.sessionId = sessionId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Note createDefault(
            Long userId,
            String content
    ) {
        return new Note(
                null,
                userId,
                content,
                NoteStatus.ACTIVE,
                false,
                NoteContextType.DEFAULT,
                null,
                null,
                null,
                null
        );
    }

    public static Note createFolder(
            Long userId,
            Long folderId,
            String content
    ) {
        return new Note(
                null,
                userId,
                content,
                NoteStatus.ACTIVE,
                false,
                NoteContextType.FOLDER,
                folderId,
                null,
                null,
                null
        );
    }

    public static Note createSession(
            Long userId,
            Long sessionId,
            String content
    ) {
        return new Note(
                null,
                userId,
                content,
                NoteStatus.ACTIVE,
                false,
                NoteContextType.SESSION,
                null,
                sessionId,
                null,
                null
        );
    }

    public static Note restore(
            Long id,
            Long userId,
            String content,
            NoteStatus status,
            boolean deleted,
            NoteContextType contextType,
            Long folderId,
            Long sessionId,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new Note(
                id,
                userId,
                content,
                status,
                deleted,
                contextType,
                folderId,
                sessionId,
                createdAt,
                updatedAt
        );
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void archive() {
        this.status = NoteStatus.ARCHIVED;
    }

    public void restoreFromArchive() {
        this.status = NoteStatus.ACTIVE;
    }

    public void delete() {
        this.deleted = true;
    }
}
