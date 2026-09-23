package com.swimming.backend.agentwork.service;

import com.swimming.backend.agentwork.exception.AgentWorkErrorCode;

import com.swimming.backend.agentwork.domain.WorkResourceType;
import com.swimming.backend.agentwork.repository.AgentWorkItemWriteRepository;
import com.swimming.backend.agentwork.repository.entity.AgentSessionEntity;
import jakarta.persistence.EntityManager;
import com.swimming.backend.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentWorkItemWriteService {
    private final AgentWorkItemWriteRepository repository;
    private final EntityManager entityManager;

    /** 호출한 트랜잭션이 끝날 때까지 Work Item을 잠근다. */
    @Transactional(propagation = Propagation.REQUIRED)
    public Long registerAndLock(Long userId, WorkResourceType resourceType, String resourceId, Instant now) {
        int inserted = repository.insertIfAbsent(userId, resourceType.name(), resourceId, now);
        if (inserted != 0 && inserted != 1) {
            throw new IllegalStateException("Work Item 등록 영향 행 수가 올바르지 않습니다.");
        }
        return repository.findOwnedResourceForUpdate(userId, resourceType, resourceId)
                .orElseThrow(() -> new BusinessException(AgentWorkErrorCode.AGENT_WORK_ITEM_NOT_FOUND)).getId();
    }

    /** 잠긴 Work Item 목록을 한 번의 JPQL로 연결한다. 세션 교체·소유권 우회를 허용하지 않는다. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void attachSession(Long userId, List<Long> workItemIds, Long sessionId, Instant now) {
        var session = entityManager.getReference(AgentSessionEntity.class, sessionId);
        if (!userId.equals(session.getUserId())) {
            throw new BusinessException(AgentWorkErrorCode.AGENT_SESSION_NOT_FOUND);
        }
        int affected = repository.attachSession(userId, workItemIds, sessionId, session, now);
        if (affected != workItemIds.size()) {
            throw new BusinessException(AgentWorkErrorCode.AGENT_SESSION_CONFLICT);
        }
    }
}
