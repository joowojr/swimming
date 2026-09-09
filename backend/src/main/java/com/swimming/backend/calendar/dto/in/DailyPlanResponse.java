package com.swimming.backend.calendar.dto.in;

import java.time.LocalDate;
import java.util.List;

public record DailyPlanResponse(
        LocalDate date,
        List<DailyPlanItemResponse> items
) {
}
