package com.swimming.backend.plan.dto;

import com.swimming.backend.task.domain.TaskStatus;

public record DailyPlanItemResponse(
        Long id,
        Long taskId,
        DailyPlanItemType itemType,
        Long folderId,
        String folderName,
        String title,
        TaskStatus status,
        boolean priority,
        boolean urgent,
        int orderIdx
) {
    public DailyPlanItemResponse(Long id, Long taskId, DailyPlanItemType itemType, Long folderId,
                                 String folderName, String title, TaskStatus status, int orderIdx) {
        this(id, taskId, itemType, folderId, folderName, title, status, false, false, orderIdx);
    }
}
