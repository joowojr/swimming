package com.swimming.backend.task.dto.web;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReorderTasksRequest(
        @NotEmpty(message = "Task 순서를 입력해 주세요")
        List<@NotNull(message = "Task ID를 입력해 주세요") Long> taskIds
) {
}
