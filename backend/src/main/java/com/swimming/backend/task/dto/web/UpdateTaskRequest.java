package com.swimming.backend.task.dto.web;

import com.swimming.backend.task.domain.TaskStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTaskRequest(
        @NotBlank(message = "Task 제목을 입력해 주세요")
        @Size(max = 255, message = "Task 제목은 255자 이하여야 합니다")
        String title,

        @NotNull(message = "Task 상태를 선택해 주세요")
        TaskStatus status,

        @NotNull(message = "Task 완료도를 입력해 주세요")
        @Min(value = 0, message = "Task 완료도는 0 이상이어야 합니다")
        @Max(value = 100, message = "Task 완료도는 100 이하여야 합니다")
        Integer completionPct
) {
}
