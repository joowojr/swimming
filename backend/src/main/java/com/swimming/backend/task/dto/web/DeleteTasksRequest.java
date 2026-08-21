package com.swimming.backend.task.dto.web;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record DeleteTasksRequest(
        @NotEmpty(message = "삭제할 Task를 입력해 주세요")
        List<@NotNull(message = "Task ID를 입력해 주세요") Long> taskIds
) {
}
