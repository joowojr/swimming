package com.swimming.backend.agentwork.dto.out;

import com.swimming.backend.agentwork.domain.AgentSessionEventType;
import com.swimming.backend.agentwork.domain.AgentType;
import com.swimming.backend.agentwork.domain.StatusSource;

import java.time.Instant;

/**
 * Agent 활동 Tab의 한 줄. payload 원문은 내보내지 않고 요약만 싣는다.
 * 끝난 세션을 다른 Agent가 다시 열 수 있으므로 agentType은 이벤트가 기록될 때의 값이다.
 */
public record AgentSessionEventResponse(
        Long id,
        Long sessionId,
        AgentType agentType,
        AgentSessionEventType eventType,
        String summary,
        StatusSource source,
        Instant createdAt
) {
}
