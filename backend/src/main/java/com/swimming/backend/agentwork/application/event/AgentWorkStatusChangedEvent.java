package com.swimming.backend.agentwork.application.event;

import com.swimming.backend.agentwork.domain.AgentWorkStatus;

import java.time.Instant;

public record AgentWorkStatusChangedEvent(Long userId, Long sessionId, AgentWorkStatus status, Instant occurredAt) {}
