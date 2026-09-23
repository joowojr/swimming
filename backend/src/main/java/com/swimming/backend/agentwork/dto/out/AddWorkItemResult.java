package com.swimming.backend.agentwork.dto.out;


/** 보드에 새로 올렸는지(created)에 따라 컨트롤러가 201과 200을 나눈다. */
public record AddWorkItemResult(AgentWorkItemResponse workItem, boolean created) {
}
