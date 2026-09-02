package com.swimming.backend.note.dto.in;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TaskOrganizeConfirmRequest(
        @NotNull
        Long noteId,

        @NotEmpty
        List<@Valid ApprovedTaskRequest> tasks
) {

    public record ApprovedTaskRequest(
            @NotBlank
            String sourceText,

            Long folderId,

            @NotBlank(message = "Task 제목을 입력해 주세요")
            @Size(max = 255, message = "Task 제목은 255자 이하여야 합니다")
            String title
    ) {
    }
}
