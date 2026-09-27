package com.swimming.backend.agentwork.domain;

public enum AgentWorkStatus {
    WORKING,
    WAITING,
    COMPLETED,
    FAILED,
    UNKNOWN;

    public boolean isEnded() {
        return this == COMPLETED || this == FAILED;
    }
}
