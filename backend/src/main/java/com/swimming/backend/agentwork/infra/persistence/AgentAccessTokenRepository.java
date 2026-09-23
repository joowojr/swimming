package com.swimming.backend.agentwork.infra.persistence;

import com.swimming.backend.agentwork.infra.persistence.entity.AgentAccessTokenEntity;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

public interface AgentAccessTokenRepository extends Repository<AgentAccessTokenEntity, Long> {
    AgentAccessTokenEntity save(AgentAccessTokenEntity entity);
    Optional<AgentAccessTokenEntity> findByTokenHash(String tokenHash);
    Optional<AgentAccessTokenEntity> findByIdAndUserId(Long id, Long userId);
    List<AgentAccessTokenEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
