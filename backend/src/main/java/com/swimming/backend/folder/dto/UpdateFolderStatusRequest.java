package com.swimming.backend.folder.dto;

import com.swimming.backend.folder.domain.FolderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateFolderStatusRequest(
        @NotNull(message = "폴더 상태를 선택해 주세요")
        FolderStatus status
) {
}
