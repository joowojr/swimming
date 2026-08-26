package com.swimming.backend.plan.repository.projection;

import com.swimming.backend.task.domain.TaskStatus;

import java.time.LocalDate;

public record DailyPlanItemQueryRow(
        Long id,
        LocalDate planDate,
        Long taskId,
        Long projectId,
        String projectName,
        String title,
        TaskStatus status,
        int orderIdx
) {}
