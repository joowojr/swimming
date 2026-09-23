package com.swimming.backend.agentwork.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record CreateAgentAccessTokenRequest(
        @NotBlank @Size(max = 100) String name,
        @Min(value = 1, message = "만료 기간은 1일 이상이어야 합니다") Integer expiresInDays
) {
}
