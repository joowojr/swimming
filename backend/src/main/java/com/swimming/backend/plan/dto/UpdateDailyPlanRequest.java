package com.swimming.backend.plan.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record UpdateDailyPlanRequest(
        @NotNull(message = "계획 날짜를 선택해 주세요")
        LocalDate date,

        @NotNull(message = "계획 Task 목록을 입력해 주세요")
        List<@NotNull(message = "Task ID를 입력해 주세요") Long> taskIds
) {
}
