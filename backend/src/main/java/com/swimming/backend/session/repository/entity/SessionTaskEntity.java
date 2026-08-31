package com.swimming.backend.session.repository.entity;

import com.swimming.backend.common.entity.BaseTimeEntity;
import com.swimming.backend.session.domain.SessionTask;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "session_tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionTaskEntity extends BaseTimeEntity {

    @EmbeddedId
    private SessionTaskId id;

    @MapsId("sessionId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private SessionEntity session;

    @Column(name = "is_completed", nullable = false)
    private Boolean isCompleted;

    private SessionTaskEntity(
            SessionEntity session,
            SessionTask task
    ) {
        this.id = SessionTaskId.of(task.taskId());
        this.session = session;
        this.isCompleted = task.isCompleted();
    }

    public static SessionTaskEntity from(
            SessionEntity session,
            SessionTask task
    ) {
        return new SessionTaskEntity(session, task);
    }

    public Long getTaskId() {
        return id.getTaskId();
    }

    public SessionTask toDomain() {
        return new SessionTask(getTaskId(), isCompleted);
    }
}
