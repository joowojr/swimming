package com.swimming.backend.plan.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReorderDailyPlanItemsRequest(
        @NotNull(message = "계획 항목 목록을 입력해 주세요")
        List<@NotNull(message = "계획 항목 ID를 입력해 주세요") Long> itemIds
) {
}
