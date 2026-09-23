package com.swimming.backend.agentwork.domain;
import com.swimming.backend.agentwork.exception.AgentWorkErrorCode;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentSessionTest {
    private static final Instant BEFORE = Instant.parse("2026-09-18T00:00:00Z");
    private static final Instant NOW = BEFORE.plusSeconds(3600);

    @Test
    @DisplayName("최초 시작은 WORKING 상태이며 시작·마지막 보고 시각이 같다")
    void startsSession() {
        AgentSession session = AgentSession.start(1L, AgentType.CODEX, null, NOW);
        assertThat(session.getStatus()).isEqualTo(AgentWorkStatus.WORKING);
        assertThat(session.getStatusSource()).isEqualTo(StatusSource.MCP_REPORT);
        assertThat(session.getStartedAt()).isEqualTo(NOW);
        assertThat(session.getLastSeenAt()).isEqualTo(NOW);
        assertThat(session.getCompletedAt()).isNull();
        assertThat(session.summary()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = AgentWorkStatus.class, names = {"COMPLETED", "FAILED"})
    @DisplayName("종료 세션은 같은 식별자로 재시작하며 이전 snapshot과 완료 시각을 초기화한다")
    void restartsEndedSession(AgentWorkStatus status) {
        AgentSession session = restored(status);
        session.restart(AgentType.CODEX, "다시 시작", NOW);
        assertThat(session.getId()).isEqualTo(100L);
        assertThat(session.getUserId()).isEqualTo(1L);
        assertThat(session.getAgentType()).isEqualTo(AgentType.CODEX);
        assertThat(session.getInstruction()).isEqualTo("다시 시작");
        assertThat(session.getStatus()).isEqualTo(AgentWorkStatus.WORKING);
        assertThat(session.getStartedAt()).isEqualTo(NOW);
        assertThat(session.getLastSeenAt()).isEqualTo(NOW);
        assertThat(session.getCompletedAt()).isNull();
        assertThat(session.getProgressSnapshot()).isNull();
        assertThat(session.getResultSnapshot()).isNull();
        assertThat(session.getErrorSnapshot()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = AgentWorkStatus.class, names = {"WORKING", "WAITING", "UNKNOWN"})
    @DisplayName("진행 중인 세션을 재시작하면 상태를 바꾸지 않고 충돌 오류를 낸다")
    void rejectsActiveSession(AgentWorkStatus status) {
        AgentSession session = restored(status);
        assertThatThrownBy(() -> session.restart(AgentType.CODEX, "다시 시작", NOW))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(AgentWorkErrorCode.AGENT_WORK_ALREADY_IN_PROGRESS));
        assertThat(session.getStatus()).isEqualTo(status);
        assertThat(session.getAgentType()).isEqualTo(AgentType.CLAUDE_CODE);
        assertThat(session.getStartedAt()).isEqualTo(BEFORE);
    }

    @ParameterizedTest
    @EnumSource(value = AgentWorkStatus.class, names = {"WORKING", "WAITING", "UNKNOWN"})
    @DisplayName("진행·대기·무응답 세션을 완료하면 결과·출처·마지막 보고·완료 시각을 갱신한다")
    void completesActiveSession(AgentWorkStatus status) {
        AgentSession session = restored(status);
        session.complete("구현 완료", Map.of("files", 3), NOW);
        assertThat(session.getStatus()).isEqualTo(AgentWorkStatus.COMPLETED);
        assertThat(session.getStatusSource()).isEqualTo(StatusSource.MCP_REPORT);
        assertThat(session.getResultSnapshot()).containsEntry("summary", "구현 완료")
                .containsEntry("details", Map.of("files", 3));
        assertThat(session.summary()).isEqualTo("구현 완료");
        assertThat(session.getLastSeenAt()).isEqualTo(NOW);
        assertThat(session.getCompletedAt()).isEqualTo(NOW);
        assertThat(session.getStartedAt()).isEqualTo(BEFORE);
    }

    @ParameterizedTest
    @EnumSource(value = AgentWorkStatus.class, names = {"COMPLETED", "FAILED"})
    @DisplayName("종료 세션은 완료 보고를 거부하고 기존 상태·결과를 유지한다")
    void rejectsCompletionAfterEnd(AgentWorkStatus status) {
        AgentSession session = restored(status);
        assertThatThrownBy(() -> session.complete("새 결과", null, NOW))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(AgentWorkErrorCode.AGENT_SESSION_ALREADY_ENDED));
        assertThat(session.getStatus()).isEqualTo(status);
        assertThat(session.getResultSnapshot()).containsEntry("summary", "이전 보고");
        assertThat(session.getLastSeenAt()).isEqualTo(BEFORE);
    }

    private AgentSession restored(AgentWorkStatus status) {
        Map<String, Object> snapshot = Map.of("summary", "이전 보고");
        return AgentSession.restore(100L, 1L, AgentType.CLAUDE_CODE, status, StatusSource.MCP_REPORT,
                "이전 지시", snapshot, snapshot, snapshot, BEFORE, BEFORE, BEFORE);
    }
}
