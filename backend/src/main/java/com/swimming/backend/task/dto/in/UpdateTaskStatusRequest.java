package com.swimming.backend.task.dto.in;

import com.swimming.backend.task.domain.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTaskStatusRequest(
        @NotNull(message = "Task 상태를 선택해 주세요")
        TaskStatus status
) {
}
