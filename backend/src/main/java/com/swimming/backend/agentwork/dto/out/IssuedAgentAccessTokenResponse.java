package com.swimming.backend.agentwork.dto.out;

import java.time.Instant;
import java.util.Set;

public record IssuedAgentAccessTokenResponse(
        Long id,
        String name,
        String token,
        Set<String> scopes,
        Instant expiresAt
) {
}
