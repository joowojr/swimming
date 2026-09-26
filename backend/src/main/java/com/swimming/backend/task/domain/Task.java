package com.swimming.backend.task.domain;

import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
public class Task {

    private final Long id;
    private final Long userId;
    private final Long folderId;
    private final Long sourceNoteId;
    private String title;
    private TaskStatus status;
    private boolean priority;
    private boolean urgent;
    private int orderIdx;
    private long matrixRank;
    /** 캘린더에 담긴 날짜. 한 task는 최대 하나의 날짜를 갖고, 담지 않았으면 null이다. */
    private final LocalDate planDate;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Task(
            Long id,
            Long userId,
            Long folderId,
            Long sourceNoteId,
            String title,
            TaskStatus status,
            boolean priority,
            boolean urgent,
            int orderIdx,
            long matrixRank,
            LocalDate planDate,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.folderId = folderId;
        this.sourceNoteId = sourceNoteId;
        this.title = title;
        this.status = status;
        this.priority = priority;
        this.urgent = urgent;
        this.orderIdx = orderIdx;
        this.matrixRank = matrixRank;
        this.planDate = planDate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Task create(Long userId, Long folderId, String title, int orderIdx) {
        return create(userId, folderId, title, orderIdx, false, false);
    }

    public static Task create(Long userId, Long folderId, String title, int orderIdx,
                              boolean priority, boolean urgent) {
        return create(userId, folderId, title, orderIdx, priority, urgent, 0L);
    }

    public static Task create(Long userId, Long folderId, String title, int orderIdx,
                              boolean priority, boolean urgent, long matrixRank) {
        return new Task(
                null,
                userId,
                folderId,
                null,
                title.trim(),
                TaskStatus.TODO,
                priority,
                urgent,
                orderIdx,
                matrixRank,
                null,
                null,
                null
        );
    }

    public static Task createFromNote(
            Long userId,
            Long folderId,
            Long sourceNoteId,
            String title,
            int orderIdx
    ) {
        return createFromNote(userId, folderId, sourceNoteId, title, orderIdx, false, false);
    }

    public static Task createFromNote(
            Long userId,
            Long folderId,
            Long sourceNoteId,
            String title,
            int orderIdx,
            boolean priority,
            boolean urgent
    ) {
        return createFromNote(userId, folderId, sourceNoteId, title, orderIdx, priority, urgent, 0L);
    }

    public static Task createFromNote(
            Long userId,
            Long folderId,
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
                folderId,
                sourceNoteId,
                title.trim(),
                TaskStatus.TODO,
                priority,
                urgent,
                orderIdx,
                matrixRank,
                null,
                null,
                null
        );
    }

    public static Task restore(
            Long id,
            Long userId,
            Long folderId,
            Long sourceNoteId,
            String title,
            TaskStatus status,
            int orderIdx,
            Instant createdAt,
            Instant updatedAt
    ) {
        return restore(id, userId, folderId, sourceNoteId, title, status, false, false, orderIdx, 0L, createdAt, updatedAt);
    }

    public static Task restore(
            Long id, Long userId, Long folderId, Long sourceNoteId, String title,
            TaskStatus status, boolean priority, boolean urgent, int orderIdx,
            Instant createdAt, Instant updatedAt
    ) {
        return restore(id, userId, folderId, sourceNoteId, title, status, priority, urgent, orderIdx, 0L, createdAt, updatedAt);
    }

    public static Task restore(
            Long id, Long userId, Long folderId, Long sourceNoteId, String title,
            TaskStatus status, boolean priority, boolean urgent, int orderIdx, long matrixRank,
            Instant createdAt, Instant updatedAt
    ) {
        return restore(id, userId, folderId, sourceNoteId, title, status, priority, urgent, orderIdx, matrixRank,
                null, createdAt, updatedAt);
    }

    public static Task restore(
            Long id, Long userId, Long folderId, Long sourceNoteId, String title,
            TaskStatus status, boolean priority, boolean urgent, int orderIdx, long matrixRank,
            LocalDate planDate, Instant createdAt, Instant updatedAt
    ) {
        return new Task(
                id,
                userId,
                folderId,
                sourceNoteId,
                title,
                status,
                priority,
                urgent,
                orderIdx,
                matrixRank,
                planDate,
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
