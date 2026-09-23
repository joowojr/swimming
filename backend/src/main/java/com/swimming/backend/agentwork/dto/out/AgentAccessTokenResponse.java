package com.swimming.backend.agentwork.dto.out;

import java.time.Instant;
import java.util.Set;

public record AgentAccessTokenResponse(
        Long id,
        String name,
        String tokenPrefix,
        String tokenSuffix,
        Set<String> scopes,
        Instant expiresAt,
        Instant lastUsedAt,
        Instant revokedAt,
        Instant createdAt
) {
}
