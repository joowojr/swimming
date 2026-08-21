package com.swimming.backend.session.dto.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record StartPersonalSessionRequest(
        @NotEmpty(message = "Task를 하나 이상 선택해 주세요")
        List<@NotNull(message = "Task ID는 비어 있을 수 없습니다") Long> taskIds,

        @NotNull(message = "집중 시간을 입력해 주세요")
        @Min(value = 60, message = "집중 시간은 60초 이상이어야 합니다")
        @Max(value = 86400, message = "집중 시간은 86400초 이하여야 합니다")
        Integer plannedDurationSec
) {
}
