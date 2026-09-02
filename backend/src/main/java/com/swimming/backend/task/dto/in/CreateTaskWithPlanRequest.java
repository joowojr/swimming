package com.swimming.backend.task.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateTaskWithPlanRequest(
        @NotBlank(message = "Task 제목을 입력해 주세요")
        @Size(max = 255, message = "Task 제목은 255자 이하여야 합니다")
        String title,
        Long projectId,
        Boolean priority,
        Boolean urgent,
        LocalDate planDate
) {
    public CreateTaskWithPlanRequest {
        priority = Boolean.TRUE.equals(priority);
        urgent = Boolean.TRUE.equals(urgent);
    }
}
