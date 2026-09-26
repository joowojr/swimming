package com.swimming.backend.agentwork.application.service;

import com.swimming.backend.agentwork.domain.AgentWorkErrorCode;

import com.swimming.backend.agentwork.domain.AgentSession;
import com.swimming.backend.agentwork.infra.persistence.AgentSessionWriteRepository;
import com.swimming.backend.agentwork.infra.persistence.entity.AgentSessionEntity;
import com.swimming.backend.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AgentSessionWriteService {
    private final AgentSessionWriteRepository repository;

    @Transactional(propagation = Propagation.REQUIRED)
    public Optional<AgentSession> findOwnedForUpdate(Long userId, Long sessionId) {
        return repository.findByIdAndUserId(sessionId, userId).map(AgentSessionEntity::toDomain);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public AgentSession create(AgentSession session) {
        return repository.saveAndFlush(AgentSessionEntity.from(session)).toDomain();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void applyState(AgentSession session) {
        AgentSessionEntity entity = repository.findByIdAndUserId(session.getId(), session.getUserId())
                .orElseThrow(() -> new BusinessException(AgentWorkErrorCode.AGENT_SESSION_NOT_FOUND));
        entity.apply(session);
    }
}
