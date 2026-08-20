package com.swimming.backend.plan.dto;

import com.swimming.backend.task.domain.TaskStatus;

public record DailyPlanItemResponse(
        Long taskId,
        Long projectId,
        String projectName,
        String title,
        TaskStatus status,
        int completionPct,
        int orderIdx
) {
}
