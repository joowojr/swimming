package com.swimming.backend.folder.dto;

import jakarta.validation.constraints.NotNull;

public record PinFolderRequest(
        @NotNull(message = "고정 여부를 입력해 주세요")
        Boolean pinned
) {
}
