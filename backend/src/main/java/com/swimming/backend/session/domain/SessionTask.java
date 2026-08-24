package com.swimming.backend.session.domain;

public record SessionTask(Long taskId) {

    public static SessionTask of(Long taskId) {
        return new SessionTask(taskId);
    }
}
