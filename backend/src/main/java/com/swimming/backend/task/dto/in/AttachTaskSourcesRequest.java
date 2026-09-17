package com.swimming.backend.task.dto.in;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** 할 일에 붙일 링크. 화면에서 여러 개를 골라 한 번에 보낸다. */
public record AttachTaskSourcesRequest(
        @NotEmpty(message = "연결할 링크를 하나 이상 골라 주세요")
        @Size(max = MAX_SIZE, message = "한 번에 연결할 수 있는 링크는 " + MAX_SIZE + "개까지입니다")
        List<@NotNull(message = "링크 ID를 입력해 주세요") UUID> sourceIds
) {
    public static final int MAX_SIZE = 5;
}
