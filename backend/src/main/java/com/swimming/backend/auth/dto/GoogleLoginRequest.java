package com.swimming.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @NotBlank(message = "Google 인증 정보가 필요합니다")
        String credential
) {
}
