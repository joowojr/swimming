package com.swimming.backend.agentwork.dto.out;

import com.swimming.backend.agentwork.domain.BoardLane;
import com.swimming.backend.agentwork.workitem.LinkedSource;

import java.util.List;

/**
 * Agent가 할 일을 시작하기 전에 읽는 맥락. 보드 카드 정보와 연결된 지식을 한 번에 담는다.
 *
 * @param workItemId 보드 카드 식별자. 아직 보드에 등록되지 않았으면 null
 * @param session    아직 Agent가 작업을 시작하지 않았으면 null
 */
public record TaskContextResponse(
        Long workItemId,
        BoardLane lane,
        WorkItemResponse task,
        AgentSessionResponse session,
        List<LinkedSource> linkedSources
) {
}
