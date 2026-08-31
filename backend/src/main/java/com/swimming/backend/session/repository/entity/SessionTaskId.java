package com.swimming.backend.session.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionTaskId implements Serializable {

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    private SessionTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public static SessionTaskId of(Long taskId) {
        return new SessionTaskId(taskId);
    }
}
