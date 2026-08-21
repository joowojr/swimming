package com.swimming.backend.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDailyPlanItemRequest(
        @NotBlank(message = "할 일을 입력해 주세요")
        @Size(max = 255, message = "할 일은 255자 이내로 입력해 주세요")
        String title
) {
}
