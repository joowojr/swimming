package com.swimming.backend.plan.domain;

import lombok.Getter;

import java.time.Instant;

@Getter
public class DailyPlanItem {

    private final Long id;
    private final Long taskId;
    private int orderIdx;
    private final Instant createdAt;
    private final Instant updatedAt;

    private DailyPlanItem(
            Long id,
            Long taskId,
            int orderIdx,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.taskId = taskId;
        this.orderIdx = orderIdx;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static DailyPlanItem createTask(Long taskId) {
        return new DailyPlanItem(null, taskId, 0, null, null);
    }

    public static DailyPlanItem restore(
            Long id,
            Long taskId,
            int orderIdx,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new DailyPlanItem(id, taskId, orderIdx, createdAt, updatedAt);
    }

    void changeOrder(int orderIdx) {
        this.orderIdx = orderIdx;
    }
}
