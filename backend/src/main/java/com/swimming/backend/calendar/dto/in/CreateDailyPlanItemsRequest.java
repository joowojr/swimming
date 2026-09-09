package com.swimming.backend.calendar.dto.in;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateDailyPlanItemsRequest(
        List<@NotNull Long> taskIds,
        Long folderId,
        @Size(max = 255, message = "할 일은 255자 이내로 입력해 주세요")
        String title,
        Boolean priority,
        Boolean urgent
) {
    public CreateDailyPlanItemsRequest {
        priority = Boolean.TRUE.equals(priority);
        urgent = Boolean.TRUE.equals(urgent);
    }

    public CreateDailyPlanItemsRequest(List<Long> taskIds, Long folderId, String title) {
        this(taskIds, folderId, title, Boolean.FALSE, Boolean.FALSE);
    }
}
