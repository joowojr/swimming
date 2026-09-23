package com.swimming.backend.agentwork.application.dto;

import com.swimming.backend.agentwork.domain.AgentWorkStatus;

import java.time.Instant;

/** SSE agent-work 이벤트의 data. 클라이언트는 이 알림을 받으면 보드 snapshot을 다시 조회한다. */
public record AgentWorkChangeResponse(Long sessionId, AgentWorkStatus status, Instant occurredAt) {
}
