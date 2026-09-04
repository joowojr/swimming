package com.swimming.backend.plan.dto;

import com.swimming.backend.plan.dto.projection.DailyPlanItemQueryRow;
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

    /** 폴더가 없거나 지워졌으면 AD_HOC이다. 조회 결과를 응답으로 옮기는 규칙을 한곳에 둔다. */
    public static DailyPlanItemResponse from(DailyPlanItemQueryRow row) {
        return new DailyPlanItemResponse(
                row.id(),
                row.taskId(),
                (row.folderId() == null || row.folderIsDeleted()) ? DailyPlanItemType.AD_HOC : DailyPlanItemType.TASK,
                row.folderId(),
                row.folderName(),
                row.title(),
                row.status(),
                row.priority(),
                row.urgent(),
                row.orderIdx()
        );
    }
}
