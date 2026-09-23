package com.swimming.backend.agentwork.application.service;

import com.swimming.backend.agentwork.domain.AgentSession;
import com.swimming.backend.agentwork.infra.persistence.AgentSessionEventWriteRepository;
import com.swimming.backend.agentwork.infra.persistence.entity.AgentSessionEntity;
import com.swimming.backend.agentwork.infra.persistence.entity.AgentSessionEventEntity;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentSessionEventWriteService {
    private final AgentSessionEventWriteRepository repository;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordStarted(AgentSession session) {
        AgentSessionEntity reference = entityManager.getReference(AgentSessionEntity.class, session.getId());
        repository.saveAndFlush(AgentSessionEventEntity.started(session, reference));
    }
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordCompleted(AgentSession session) {
        AgentSessionEntity reference = entityManager.getReference(AgentSessionEntity.class, session.getId());
        repository.saveAndFlush(AgentSessionEventEntity.completed(session, reference));
    }
}

