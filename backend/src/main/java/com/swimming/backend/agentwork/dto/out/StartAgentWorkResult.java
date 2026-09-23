package com.swimming.backend.agentwork.dto.out;


/** 세션을 새로 만들었는지(created), 끝난 세션을 다시 열었는지에 따라 컨트롤러가 201과 200을 나눈다. */
public record StartAgentWorkResult(AgentSessionResponse session, boolean created) {
}
