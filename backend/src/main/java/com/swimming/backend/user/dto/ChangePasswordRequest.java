package com.swimming.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "현재 비밀번호를 입력해 주세요")
        String currentPassword,

        @NotBlank(message = "새 비밀번호를 입력해 주세요")
        @Size(min = 10, max = 64, message = "새 비밀번호는 10자 이상 64자 이하로 입력해 주세요")
        @Pattern(
                regexp = "(?s).*(?:\\d|[^\\p{L}\\p{N}\\s]).*",
                message = "새 비밀번호는 숫자 또는 특수문자를 하나 이상 포함해 주세요"
        )
        String newPassword
) {
}
