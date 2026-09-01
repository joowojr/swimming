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
    private boolean priority;
    private boolean urgent;
    private int orderIdx;
    private long matrixRank;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private Task(
            Long id,
            Long userId,
            Long projectId,
            Long sourceNoteId,
            String title,
            TaskStatus status,
            boolean priority,
            boolean urgent,
            int orderIdx,
            long matrixRank,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.projectId = projectId;
        this.sourceNoteId = sourceNoteId;
        this.title = title;
        this.status = status;
        this.priority = priority;
        this.urgent = urgent;
        this.orderIdx = orderIdx;
        this.matrixRank = matrixRank;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Task create(Long userId, Long projectId, String title, int orderIdx) {
        return create(userId, projectId, title, orderIdx, false, false);
    }

    public static Task create(Long userId, Long projectId, String title, int orderIdx,
                              boolean priority, boolean urgent) {
        return create(userId, projectId, title, orderIdx, priority, urgent, 0L);
    }

    public static Task create(Long userId, Long projectId, String title, int orderIdx,
                              boolean priority, boolean urgent, long matrixRank) {
        return new Task(
                null,
                userId,
                projectId,
                null,
                title.trim(),
                TaskStatus.TODO,
                priority,
                urgent,
                orderIdx,
                matrixRank,
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
        return createFromNote(userId, projectId, sourceNoteId, title, orderIdx, false, false);
    }

    public static Task createFromNote(
            Long userId,
            Long projectId,
            Long sourceNoteId,
            String title,
            int orderIdx,
            boolean priority,
            boolean urgent
    ) {
        return createFromNote(userId, projectId, sourceNoteId, title, orderIdx, priority, urgent, 0L);
    }

    public static Task createFromNote(
            Long userId,
            Long projectId,
            Long sourceNoteId,
            String title,
            int orderIdx,
            boolean priority,
            boolean urgent,
            long matrixRank
    ) {
        return new Task(
                null,
                userId,
                projectId,
                sourceNoteId,
                title.trim(),
                TaskStatus.TODO,
                priority,
                urgent,
                orderIdx,
                matrixRank,
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
        return restore(id, userId, projectId, sourceNoteId, title, status, false, false, orderIdx, 0L, createdAt, updatedAt);
    }

    public static Task restore(
            Long id, Long userId, Long projectId, Long sourceNoteId, String title,
            TaskStatus status, boolean priority, boolean urgent, int orderIdx,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        return restore(id, userId, projectId, sourceNoteId, title, status, priority, urgent, orderIdx, 0L, createdAt, updatedAt);
    }

    public static Task restore(
            Long id, Long userId, Long projectId, Long sourceNoteId, String title,
            TaskStatus status, boolean priority, boolean urgent, int orderIdx, long matrixRank,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        return new Task(
                id,
                userId,
                projectId,
                sourceNoteId,
                title,
                status,
                priority,
                urgent,
                orderIdx,
                matrixRank,
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

    public void moveTo(TaskMatrixSection section, long matrixRank) {
        this.priority = section.isPriority();
        this.urgent = section.isUrgent();
        this.matrixRank = matrixRank;
    }

    public void changeMatrixRank(long matrixRank) {
        this.matrixRank = matrixRank;
    }
}
