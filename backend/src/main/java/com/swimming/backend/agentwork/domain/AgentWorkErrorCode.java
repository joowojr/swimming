package com.swimming.backend.agentwork.domain;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum AgentWorkErrorCode {
    AGENT_WORK_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Cowork Board 항목을 찾을 수 없습니다"),
    AGENT_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Agent 세션을 찾을 수 없습니다"),
    AGENT_SESSION_ALREADY_ENDED(HttpStatus.CONFLICT, "이미 끝난 Agent 세션입니다"),
    AGENT_SESSION_CONFLICT(HttpStatus.CONFLICT, "서로 다른 세션에 연결된 할 일을 함께 시작할 수 없습니다."),
    AGENT_WORK_ALREADY_IN_PROGRESS(HttpStatus.CONFLICT, "이미 Agent가 작업 중인 할 일입니다"),
    AGENT_ACCESS_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "Agent 액세스 토큰을 찾을 수 없습니다"),
    AGENT_ACCESS_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Agent 액세스 토큰이 유효하지 않습니다"),
    AGENT_ACCESS_TOKEN_SCOPE_DENIED(HttpStatus.FORBIDDEN, "Agent 액세스 토큰에 필요한 권한이 없습니다");

    private final HttpStatus status;
    private final String message;

    AgentWorkErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
