package com.swimming.backend.plan.dto;

import java.time.LocalDate;
import java.util.List;

public record DailyPlanResponse(
        LocalDate date,
        List<DailyPlanItemResponse> items
) {
}
