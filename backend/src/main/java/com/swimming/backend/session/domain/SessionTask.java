package com.swimming.backend.session.domain;

public record SessionTask(Long taskId, Boolean isCompleted) {

    public static SessionTask of(Long taskId) {
        return new SessionTask(taskId, null);
    }

    public SessionTask recordCompletion(boolean completed) {
        return new SessionTask(taskId, completed);
    }
}
