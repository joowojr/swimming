package com.swimming.backend.agentwork.application.dto;

import com.swimming.backend.agentwork.domain.WorkResourceType;
import java.time.Instant;

/** 보드 등록 정보. Entity나 지연 로딩 연관 관계를 UseCase로 전달하지 않는다. */
public record AgentWorkItemRow(Long id, WorkResourceType resourceType, String resourceId,
                               Long sessionId, Instant createdAt) {
}
