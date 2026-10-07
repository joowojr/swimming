package com.swimming.backend.agentwork.application.service;

import com.swimming.backend.agentwork.application.dto.AgentWorkItemRow;
import com.swimming.backend.agentwork.infra.persistence.AgentWorkItemReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRED, readOnly = true)
public class AgentWorkItemReadService {
    private final AgentWorkItemReadRepository repository;

    public List<AgentWorkItemRow> findBoardItems(Long userId) {
        return repository.findBoardItems(userId);
    }

    public Optional<Long> findSessionId(Long userId, Long workItemId) {
        return repository.findSessionId(userId, workItemId);
    }

    /** 떼어 낸 할 일을 이벤트에 남길 때 쓰는 자원 식별자. */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Optional<String> findResourceId(Long userId, Long workItemId) {
        return repository.findResourceId(userId, workItemId);
    }

    public List<Long> findIdsBySession(Long userId, Long sessionId) {
        return repository.findIdsBySession(userId, sessionId);
    }
}
