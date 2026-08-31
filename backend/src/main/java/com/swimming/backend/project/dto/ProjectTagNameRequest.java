package com.swimming.backend.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectTagNameRequest(
        @NotBlank(message = "태그 이름을 입력해 주세요")
        @Size(max = 30, message = "태그 이름은 30자 이하여야 합니다")
        String name
) {
}
