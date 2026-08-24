package com.swimming.backend.session.repository.entity;

import com.swimming.backend.session.domain.SessionTask;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionTaskEmbeddable {

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    private SessionTaskEmbeddable(Long taskId) {
        this.taskId = taskId;
    }

    public static SessionTaskEmbeddable from(SessionTask task) {
        return new SessionTaskEmbeddable(task.taskId());
    }

    public SessionTask toDomain() {
        return new SessionTask(taskId);
    }
}
