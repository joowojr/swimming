package com.swimming.backend.agentwork.application.dto;

import com.swimming.backend.agentwork.domain.AgentType;
import com.swimming.backend.agentwork.domain.AgentWorkStatus;
import com.swimming.backend.agentwork.domain.StatusSource;

import java.time.Instant;
import java.util.List;

/** summary: 끝난 세션은 완료·실패 보고, 진행 중인 세션은 마지막 진행·대기 보고의 요약. */
public record AgentSessionResponse(
        Long id,
        List<Long> workItemIds,
        AgentType agentType,
        AgentWorkStatus status,
        StatusSource statusSource,
        String instruction,
        String summary,
        Instant startedAt,
        Instant lastSeenAt,
        Instant completedAt
) {
}
