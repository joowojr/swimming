package com.swimming.backend.note.repository.entity;

import com.swimming.backend.common.entity.BaseTimeEntity;
import com.swimming.backend.note.domain.NoteContextType;
import com.swimming.backend.note.domain.NoteStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "notes",
        indexes = {
                @Index(
                        name = "idx_notes_user_status_created_at",
                        columnList = "user_id, status, is_deleted, created_at"
                ),
                @Index(
                        name = "idx_notes_user_project_status_created_at",
                        columnList = "user_id, project_id, status, is_deleted, created_at"
                ),
                @Index(
                        name = "idx_notes_user_session_status_created_at",
                        columnList = "user_id, session_id, status, is_deleted, created_at"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoteEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Lob
    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NoteStatus status;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "session_id")
    private Long sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "context_type", nullable = false, length = 20)
    private NoteContextType contextType;

    @Builder
    private NoteEntity(
            Long userId,
            String content,
            NoteStatus status,
            boolean deleted,
            Long projectId,
            Long sessionId,
            NoteContextType contextType
    ) {
        this.userId = userId;
        this.content = content;
        this.status = status;
        this.deleted = deleted;
        this.projectId = projectId;
        this.sessionId = sessionId;
        this.contextType = contextType;
    }

    public void update(
            String content,
            NoteStatus status,
            boolean deleted
    ) {
        this.content = content;
        this.status = status;
        this.deleted = deleted;
    }

}
