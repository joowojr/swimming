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

import java.util.List;

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
    /** 세션에 할 일을 연결했다. 다른 세션에서 옮겨 왔으면 그 세션에는 해제 기록을 함께 남긴다. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordWorkItemLinked(AgentSession session, List<String> resourceIds) {
        AgentSessionEntity reference = entityManager.getReference(AgentSessionEntity.class, session.getId());
        repository.saveAndFlush(AgentSessionEventEntity.workItemLinked(session, reference, resourceIds));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordWorkItemUnlinked(AgentSession session, List<String> resourceIds) {
        AgentSessionEntity reference = entityManager.getReference(AgentSessionEntity.class, session.getId());
        repository.saveAndFlush(AgentSessionEventEntity.workItemUnlinked(session, reference, resourceIds));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordCompleted(AgentSession session) {
        AgentSessionEntity reference = entityManager.getReference(AgentSessionEntity.class, session.getId());
        repository.saveAndFlush(AgentSessionEventEntity.completed(session, reference));
    }
}

