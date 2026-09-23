package com.swimming.backend.agentwork.domain;

public enum AgentSessionEventType {
    STARTED,
    PROGRESS_REPORTED,
    WAITING_FOR_USER,
    COMPLETED,
    FAILED,
    SIGNAL_LOST
}
