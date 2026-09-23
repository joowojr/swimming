package com.swimming.backend.agentwork.application.service;

import com.swimming.backend.agentwork.application.dto.AgentSessionEventResponse;
import com.swimming.backend.agentwork.infra.persistence.AgentSessionEventReadRepository;
import com.swimming.backend.agentwork.infra.persistence.entity.AgentSessionEventEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRED, readOnly = true)
public class AgentSessionEventReadService {
    private final AgentSessionEventReadRepository repository;

    public List<AgentSessionEventResponse> findAllOwnedBySession(Long userId, Long sessionId) {
        return repository.findAllOwnedBySession(userId, sessionId).stream()
                .map(this::toResponse)
                .toList();
    }

    private AgentSessionEventResponse toResponse(AgentSessionEventEntity event) {
        Object summary = event.getPayload() == null ? null : event.getPayload().get("summary");
        return new AgentSessionEventResponse(
                event.getId(), event.getSession().getId(), event.getAgentType(), event.getEventType(),
                summary instanceof String value ? value : null, event.getSource(), event.getCreatedAt());
    }
}
