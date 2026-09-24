package com.swimming.backend.calendar.dto.in;

import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.projection.PlannedTaskRow;

/** 캘린더의 한 줄. 한 할 일은 날짜 하나에만 담기므로 taskId가 곧 항목의 식별자다. */
public record DailyPlanItemResponse(
        Long taskId,
        DailyPlanItemType itemType,
        Long folderId,
        String folderName,
        String title,
        TaskStatus status,
        boolean priority,
        boolean urgent
) {
    /** 폴더가 없거나 지워졌으면 AD_HOC이다. 조회 결과를 응답으로 옮기는 규칙을 한곳에 둔다. */
    public static DailyPlanItemResponse from(PlannedTaskRow row) {
        return new DailyPlanItemResponse(
                row.taskId(),
                (row.folderId() == null || Boolean.TRUE.equals(row.folderIsDeleted()))
                        ? DailyPlanItemType.AD_HOC
                        : DailyPlanItemType.TASK,
                row.folderId(),
                row.folderName(),
                row.title(),
                row.status(),
                row.priority(),
                row.urgent()
        );
    }
}
