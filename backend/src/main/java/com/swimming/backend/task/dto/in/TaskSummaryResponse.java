package com.swimming.backend.task.dto.in;

import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.domain.TaskStatus;

public record TaskSummaryResponse(
        Long id,
        String title,
        TaskStatus status,
        int orderIdx
) {
    public static TaskSummaryResponse from(Task task) {
        return new TaskSummaryResponse(
                task.getId(),
                task.getTitle(),
                task.getStatus(),
                task.getOrderIdx()
        );
    }
}
