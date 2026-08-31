package com.swimming.backend.task.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTaskTitleRequest(
        @NotBlank(message = "Task 제목을 입력해 주세요")
        @Size(max = 255, message = "Task 제목은 255자 이하여야 합니다")
        String title
) {
}
