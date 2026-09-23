package com.swimming.backend.agentwork.service;

import com.swimming.backend.agentwork.domain.AgentSession;
import com.swimming.backend.agentwork.repository.AgentSessionReadRepository;
import com.swimming.backend.agentwork.repository.entity.AgentSessionEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRED, readOnly = true)
public class AgentSessionReadService {
    private final AgentSessionReadRepository repository;

    public List<AgentSession> findAllOwned(Long userId, Collection<Long> sessionIds) {
        if (sessionIds.isEmpty()) {
            return List.of();
        }
        return repository.findAllByUserIdAndIdIn(userId, sessionIds).stream()
                .map(AgentSessionEntity::toDomain).toList();
    }
}
