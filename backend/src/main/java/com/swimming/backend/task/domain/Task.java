package com.swimming.backend.task.domain;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class Task {

    private final Long id;
    private final Long projectId;
    private String title;
    private TaskStatus status;
    private int completionPct;
    private int orderIdx;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private Task(
            Long id,
            Long projectId,
            String title,
            TaskStatus status,
            int completionPct,
            int orderIdx,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.projectId = projectId;
        this.title = title;
        this.status = status;
        this.completionPct = completionPct;
        this.orderIdx = orderIdx;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Task create(Long projectId, String title, int orderIdx) {
        return new Task(
                null,
                projectId,
                title.trim(),
                TaskStatus.TODO,
                0,
                orderIdx,
                null,
                null
        );
    }

    public static Task restore(
            Long id,
            Long projectId,
            String title,
            TaskStatus status,
            int completionPct,
            int orderIdx,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        return new Task(
                id,
                projectId,
                title,
                status,
                completionPct,
                orderIdx,
                createdAt,
                updatedAt
        );
    }

    public void update(String title, TaskStatus status, int completionPct) {
        this.title = title.trim();
        this.status = status;
        this.completionPct = completionPct;
    }
}
