package com.swimming.backend.plan.dto.projection;

import com.swimming.backend.task.domain.TaskStatus;

import java.time.LocalDate;

public record DailyPlanItemQueryRow(
        Long id,
        LocalDate planDate,
        Long taskId,
        Long projectId,
        String projectName,
        Boolean projectIsDeleted,
        String title,
        TaskStatus status,
        boolean priority,
        boolean urgent,
        int orderIdx
) {
    public DailyPlanItemQueryRow(Long id, LocalDate planDate, Long taskId, Long projectId,
                                 String projectName, Boolean projectIsDeleted, String title,
                                 TaskStatus status, int orderIdx) {
        this(id, planDate, taskId, projectId, projectName, projectIsDeleted, title, status, false, false, orderIdx);
    }
}
