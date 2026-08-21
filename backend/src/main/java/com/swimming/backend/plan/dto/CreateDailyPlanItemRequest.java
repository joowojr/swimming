package com.swimming.backend.plan.dto;

import jakarta.validation.constraints.Size;

public record CreateDailyPlanItemRequest(
        Long taskId,
        Long projectId,
        @Size(max = 255, message = "할 일은 255자 이내로 입력해 주세요")
        String title
) {
}
