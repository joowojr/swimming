package com.swimming.backend.agentwork.application.dto;


import java.util.List;

/** Lane별 카드. 한 Lane 안에서는 중요+즉시 → 즉시 → 중요 → lastActivityAt 최신 순이다. */
public record AgentBoardResponse(
        List<AgentWorkItemResponse> notStarted,
        List<AgentWorkItemResponse> working,
        List<AgentWorkItemResponse> waiting,
        List<AgentWorkItemResponse> completed,
        List<AgentWorkItemResponse> attention
) {
}
