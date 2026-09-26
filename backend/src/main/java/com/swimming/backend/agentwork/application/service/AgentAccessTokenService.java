package com.swimming.backend.agentwork.application.service;

import com.swimming.backend.agentwork.domain.AgentWorkErrorCode;

import com.swimming.backend.agentwork.domain.AgentAccessTokenScope;
import com.swimming.backend.agentwork.interfaces.router.dto.CreateAgentAccessTokenRequest;
import com.swimming.backend.agentwork.application.dto.AgentAccessTokenResponse;
import com.swimming.backend.agentwork.application.dto.IssuedAgentAccessTokenResponse;
import com.swimming.backend.agentwork.infra.persistence.AgentAccessTokenRepository;
import com.swimming.backend.agentwork.infra.persistence.entity.AgentAccessTokenEntity;
import com.swimming.backend.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentAccessTokenService {
    private static final String PREFIX = "swm_pat_";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> DEFAULT_SCOPES = EnumSet.allOf(AgentAccessTokenScope.class).stream()
            .map(AgentAccessTokenScope::value).collect(Collectors.toUnmodifiableSet());

    private final AgentAccessTokenRepository repository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRED)
    public IssuedAgentAccessTokenResponse issue(Long userId, CreateAgentAccessTokenRequest request) {
        String token = PREFIX + randomPart();
        Instant expiresAt = request.expiresInDays() == null
                ? null : clock.instant().plus(request.expiresInDays(), ChronoUnit.DAYS);
        AgentAccessTokenEntity entity = AgentAccessTokenEntity.issue(
                userId, request.name(), token.substring(0, Math.min(token.length(), 8)), token.substring(token.length() - 6),
                hash(token), String.join(",", DEFAULT_SCOPES), expiresAt);
        repository.save(entity);
        return new IssuedAgentAccessTokenResponse(entity.getId(), entity.getName(), token, DEFAULT_SCOPES, expiresAt);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public List<AgentAccessTokenResponse> findAll(Long userId) {
        return repository.findAllByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void revoke(Long userId, Long tokenId) {
        AgentAccessTokenEntity token = repository.findByIdAndUserId(tokenId, userId)
                .orElseThrow(() -> new BusinessException(AgentWorkErrorCode.AGENT_ACCESS_TOKEN_NOT_FOUND));
        token.revoke(clock.instant());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public AgentAccessTokenEntity authenticate(String rawToken) {
        AgentAccessTokenEntity token = repository.findByTokenHash(hash(rawToken))
                .filter(entity -> entity.isActive(clock.instant()))
                .orElseThrow(() -> new BusinessException(AgentWorkErrorCode.AGENT_ACCESS_TOKEN_INVALID));
        token.markUsed(clock.instant());
        return token;
    }

    public boolean hasScope(AgentAccessTokenEntity token, String scope) {
        return List.of(token.getScopes().split(",")).contains(scope);
    }

    public String hash(String rawToken) {
        try {
            return HexFormatHolder.hex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다", exception);
        }
    }

    private String randomPart() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private AgentAccessTokenResponse toResponse(AgentAccessTokenEntity token) {
        return new AgentAccessTokenResponse(token.getId(), token.getName(), token.getTokenPrefix(), token.getTokenSuffix(),
                Set.of(token.getScopes().split(",")), token.getExpiresAt(), token.getLastUsedAt(),
                token.getRevokedAt(), token.getCreatedAt());
    }

    private static final class HexFormatHolder {
        private static String hex(byte[] bytes) {
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) builder.append(String.format("%02x", value));
            return builder.toString();
        }
    }
}
