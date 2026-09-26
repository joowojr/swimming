package com.swimming.backend.agentwork.domain;

/**
 * Cowork Board의 Lane. 카드(할 일) 하나에 세션은 최대 하나이고, 그 세션 상태가 Lane을 정한다.
 *
 * <p>세션 없음 → NOT_STARTED, WORKING → WORKING, WAITING → WAITING, COMPLETED → COMPLETED,
 * FAILED·UNKNOWN → ATTENTION
 */
public enum BoardLane {
    NOT_STARTED,
    WORKING,
    WAITING,
    COMPLETED,
    ATTENTION
}
