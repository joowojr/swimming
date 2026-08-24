package com.swimming.backend.session.dto.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record EndSessionRequest(
        @Size(max = 255, message = "한 줄 기록은 255자 이하여야 합니다")
        String summary,

        @Valid
        List<TaskResult> taskResults
) {

    public record TaskResult(
            @NotNull(message = "Task ID를 입력해 주세요")
            @Positive(message = "Task ID는 양수여야 합니다")
            Long taskId,

            @NotNull(message = "완료 여부를 입력해 주세요")
            Boolean isCompleted
    ) {
    }
}
