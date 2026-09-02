package com.swimming.backend.folder.dto;

import com.swimming.backend.folder.domain.FolderStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateFolderRequest(
        @NotBlank(message = "프로젝트 이름을 입력해 주세요")
        @Size(max = 255, message = "프로젝트 이름은 255자 이하여야 합니다")
        String name,

        @NotBlank(message = "프로젝트 설명을 입력해 주세요")
        String description,

        LocalDate targetDate,

        @NotNull(message = "프로젝트 상태를 선택해 주세요")
        FolderStatus status,

        Long tagId
) {
}
