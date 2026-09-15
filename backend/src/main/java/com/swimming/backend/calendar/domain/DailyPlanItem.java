package com.swimming.backend.calendar.domain;

import lombok.Getter;

import java.time.Instant;

/** 어떤 날짜에 어떤 Task가 담겼는지. 순서는 담은 순(id)이라 따로 들고 있지 않는다. */
@Getter
public class DailyPlanItem {

    private final Long id;
    private final Long taskId;
    private final Instant createdAt;
    private final Instant updatedAt;

    private DailyPlanItem(Long id, Long taskId, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.taskId = taskId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static DailyPlanItem createTask(Long taskId) {
        return new DailyPlanItem(null, taskId, null, null);
    }

    public static DailyPlanItem restore(Long id, Long taskId, Instant createdAt, Instant updatedAt) {
        return new DailyPlanItem(id, taskId, createdAt, updatedAt);
    }
}
