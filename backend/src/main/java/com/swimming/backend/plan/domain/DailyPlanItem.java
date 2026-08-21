package com.swimming.backend.plan.domain;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class DailyPlanItem {

    private final Long id;
    private final Long taskId;
    private String title;
    private int orderIdx;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private DailyPlanItem(
            Long id,
            Long taskId,
            String title,
            int orderIdx,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.taskId = taskId;
        this.title = title;
        this.orderIdx = orderIdx;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static DailyPlanItem createTask(Long taskId) {
        return new DailyPlanItem(null, taskId, null, 0, null, null);
    }

    public static DailyPlanItem createAdHoc(String title) {
        return new DailyPlanItem(null, null, title.trim(), 0, null, null);
    }

    public static DailyPlanItem restore(
            Long id,
            Long taskId,
            String title,
            int orderIdx,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        return new DailyPlanItem(id, taskId, title, orderIdx, createdAt, updatedAt);
    }

    public void changeAdHocTitle(String title) {
        if (taskId != null) {
            throw new IllegalStateException("Task 기반 계획 항목의 제목은 변경할 수 없습니다");
        }
        this.title = title.trim();
    }

    void changeOrder(int orderIdx) {
        this.orderIdx = orderIdx;
    }
}
