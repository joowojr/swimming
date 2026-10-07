package com.swimming.backend.agentwork.domain;

public enum AgentSessionEventType {
    STARTED,
    PROGRESS_REPORTED,
    WAITING_FOR_USER,
    COMPLETED,
    FAILED,
    SIGNAL_LOST,
    /** 할 일을 세션에 연결했다. 다른 세션에서 옮겨 온 경우도 포함한다. */
    WORK_ITEM_LINKED,
    /** 할 일을 세션에서 떼었다. 다른 세션으로 옮겨 간 경우도 포함한다. */
    WORK_ITEM_UNLINKED
}
