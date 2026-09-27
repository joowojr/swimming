package com.swimming.backend.agentwork.infra.persistence.entity;

import com.swimming.backend.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "agent_access_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentAccessTokenEntity extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "token_prefix", nullable = false, length = 30)
    private String tokenPrefix;

    @Column(name = "token_suffix", nullable = false, length = 6)
    private String tokenSuffix;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 500)
    private String scopes;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public static AgentAccessTokenEntity issue(
            Long userId, String name, String tokenPrefix, String tokenSuffix, String tokenHash,
            String scopes, Instant expiresAt
    ) {
        AgentAccessTokenEntity entity = new AgentAccessTokenEntity();
        entity.userId = userId;
        entity.name = name;
        entity.tokenPrefix = tokenPrefix;
        entity.tokenSuffix = tokenSuffix;
        entity.tokenHash = tokenHash;
        entity.scopes = scopes;
        entity.expiresAt = expiresAt;
        return entity;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }

    public void markUsed(Instant now) {
        this.lastUsedAt = now;
    }

    public void revoke(Instant now) {
        this.revokedAt = now;
    }
}
