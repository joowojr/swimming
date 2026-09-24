package com.swimming.backend.task.dto.projection;

import com.swimming.backend.task.domain.TaskStatus;

import java.time.LocalDate;

/** 캘린더에 담긴 할 일 한 줄. 폴더가 없거나 지워졌는지는 캘린더가 판단한다. */
public record PlannedTaskRow(
        Long taskId,
        LocalDate planDate,
        Long folderId,
        String folderName,
        Boolean folderIsDeleted,
        String title,
        TaskStatus status,
        boolean priority,
        boolean urgent
) {
}
