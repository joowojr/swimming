package com.swimming.backend.note.repository.entity;

import com.swimming.backend.common.entity.BaseTimeEntity;
import com.swimming.backend.note.domain.TaskOrganizerRunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

@Entity
@Table(name = "task_organizer_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskOrganizerRunEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "note_id", nullable = false)
    private Long noteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskOrganizerRunStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", nullable = false, columnDefinition = "jsonb")
    private JsonNode inputSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preview_snapshot", nullable = false, columnDefinition = "jsonb")
    private JsonNode previewSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feedback_snapshot", columnDefinition = "jsonb")
    private JsonNode feedbackSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "llm_calls", nullable = false, columnDefinition = "jsonb")
    private JsonNode llmCalls;

    private TaskOrganizerRunEntity(
            Long userId,
            Long noteId,
            JsonNode inputSnapshot,
            JsonNode previewSnapshot,
            JsonNode llmCalls
    ) {
        this.userId = userId;
        this.noteId = noteId;
        this.status = TaskOrganizerRunStatus.PREVIEWED;
        this.inputSnapshot = inputSnapshot;
        this.previewSnapshot = previewSnapshot;
        this.llmCalls = llmCalls;
    }

    public static TaskOrganizerRunEntity previewed(
            Long userId,
            Long noteId,
            JsonNode inputSnapshot,
            JsonNode previewSnapshot,
            JsonNode llmCalls
    ) {
        return new TaskOrganizerRunEntity(
                userId, noteId, inputSnapshot, previewSnapshot, llmCalls
        );
    }

    public void confirm(JsonNode feedbackSnapshot) {
        this.feedbackSnapshot = feedbackSnapshot;
        this.status = TaskOrganizerRunStatus.CONFIRMED;
    }
}
