package com.swimming.backend.calendar.dto.projection;

import com.swimming.backend.task.domain.TaskStatus;

import java.time.LocalDate;

public record DailyPlanItemQueryRow(
        Long id,
        LocalDate planDate,
        Long taskId,
        Long folderId,
        String folderName,
        Boolean folderIsDeleted,
        String title,
        TaskStatus status,
        boolean priority,
        boolean urgent
) {
    public DailyPlanItemQueryRow(Long id, LocalDate planDate, Long taskId, Long folderId,
                                 String folderName, Boolean folderIsDeleted, String title,
                                 TaskStatus status) {
        this(id, planDate, taskId, folderId, folderName, folderIsDeleted, title, status, false, false);
    }
}
