package com.swimming.backend.task.dto.in;

import jakarta.validation.constraints.NotBlank;

public record TaskPlacementRequest(
        @NotBlank(message = "Task 정렬 범위를 입력해 주세요")
        String scope,
        @NotBlank(message = "Task 이동 영역을 입력해 주세요")
        String targetSection,
        Long previousTaskId,
        Long nextTaskId
) {
}
