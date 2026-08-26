package com.swimming.backend.task.domain;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class Task {

    private final Long id;
    private final Long userId;
    private final Long projectId;
    private final Long sourceNoteId;
    private String title;
    private TaskStatus status;
    private int orderIdx;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private Task(
            Long id,
            Long userId,
            Long projectId,
            Long sourceNoteId,
            String title,
            TaskStatus status,
            int orderIdx,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.projectId = projectId;
        this.sourceNoteId = sourceNoteId;
        this.title = title;
        this.status = status;
        this.orderIdx = orderIdx;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Task create(Long userId, Long projectId, String title, int orderIdx) {
        return new Task(
                null,
                userId,
                projectId,
                null,
                title.trim(),
                TaskStatus.TODO,
                orderIdx,
                null,
                null
        );
    }

    public static Task createFromNote(
            Long userId,
            Long projectId,
            Long sourceNoteId,
            String title,
            int orderIdx
    ) {
        return new Task(
                null,
                userId,
                projectId,
                sourceNoteId,
                title.trim(),
                TaskStatus.TODO,
                orderIdx,
                null,
                null
        );
    }

    public static Task restore(
            Long id,
            Long userId,
            Long projectId,
            Long sourceNoteId,
            String title,
            TaskStatus status,
            int orderIdx,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        return new Task(
                id,
                userId,
                projectId,
                sourceNoteId,
                title,
                status,
                orderIdx,
                createdAt,
                updatedAt
        );
    }

    public void changeTitle(String title) {
        this.title = title.trim();
    }

    public void changeStatus(TaskStatus status) {
        this.status = status;
    }
}
