package com.swimming.backend.agentwork.domain;

public enum AgentAccessTokenScope {
    AGENT_WORK_READ("agent-work:read"),
    AGENT_WORK_WRITE("agent-work:write");

    private final String value;

    AgentAccessTokenScope(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
