package com.swimming.backend.agentwork.dto.out;

import com.swimming.backend.agentwork.domain.BoardLane;

import java.time.Instant;

/**
 * Cowork Board의 카드 하나(= 할 일 하나). 여러 할 일이 하나의 Agent 세션을 공유할 수 있다.
 *
 * @param id             agent_work_items의 식별자. 아직 등록되지 않은 Task는 null
 * @param session        아직 Agent가 작업을 시작하지 않았으면 null
 * @param lastActivityAt 세션의 lastSeenAt. 세션이 없으면 Task 생성 시각
 */
public record AgentWorkItemResponse(
        Long id,
        BoardLane lane,
        WorkItemResponse workItem,
        AgentSessionResponse session,
        Instant lastActivityAt
) {
}
