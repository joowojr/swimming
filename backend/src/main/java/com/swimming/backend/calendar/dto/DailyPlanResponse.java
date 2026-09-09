package com.swimming.backend.calendar.dto;

import java.time.LocalDate;
import java.util.List;

public record DailyPlanResponse(
        LocalDate date,
        List<DailyPlanItemResponse> items
) {
}
