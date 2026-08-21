package com.swimming.backend.session.domain;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionTest {

    private static final Instant STARTED_AT = Instant.parse("2026-08-20T00:00:00Z");

    @Test
    @DisplayName("개인 세션을 진행 상태로 생성한다")
    void startsPersonalSession() {
        Session session = Session.startPersonal(1L, List.of(10L, 11L), 1500);

        assertThat(session.getId()).isNull();
        assertThat(session.getUserId()).isEqualTo(1L);
        assertThat(session.getType()).isEqualTo(SessionType.PERSONAL);
        assertThat(session.getTaskIds()).containsExactly(10L, 11L);
        assertThat(session.getPlannedDurationSec()).isEqualTo(1500);
        assertThat(session.getStartedAt()).isNull();
        assertThat(session.getActualDurationSec()).isNull();
        assertThat(session.getEndedAt()).isNull();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("생성 시 전달한 Task 목록을 외부에서 변경할 수 없다")
    void protectsTaskIds() {
        List<Long> taskIds = new java.util.ArrayList<>(List.of(10L, 11L));
        Session session = Session.startPersonal(1L, taskIds, 1500);

        taskIds.add(12L);

        assertThat(session.getTaskIds()).containsExactly(10L, 11L);
        assertThatThrownBy(() -> session.getTaskIds().add(12L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("계획 시간 전에 종료하면 실제 시간과 중도 종료 상태를 기록한다")
    void endsAsInterruptedBeforePlannedDuration() {
        Session session = startedSession();
        Instant endedAt = STARTED_AT.plusSeconds(600);

        session.end(endedAt);

        assertThat(session.getActualDurationSec()).isEqualTo(600);
        assertThat(session.getEndedAt()).isEqualTo(endedAt);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.INTERRUPTED);
    }

    @Test
    @DisplayName("계획 시간을 채운 뒤 종료하면 실제 시간과 완료 상태를 기록한다")
    void endsAsCompletedAfterPlannedDuration() {
        Session session = startedSession();

        session.end(STARTED_AT.plusSeconds(1560));

        assertThat(session.getActualDurationSec()).isEqualTo(1560);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
    }

    @Test
    @DisplayName("서버 종료 시각이 시작보다 이르면 0초 종료 기록으로 보정한다")
    void clampsEndTimeBeforeStart() {
        Session session = startedSession();

        session.end(STARTED_AT.minusSeconds(1));

        assertThat(session.getActualDurationSec()).isZero();
        assertThat(session.getEndedAt()).isEqualTo(STARTED_AT);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.INTERRUPTED);
    }

    @Test
    @DisplayName("이미 종료한 세션은 다시 종료할 수 없다")
    void rejectsRepeatedEnd() {
        Session session = startedSession();
        session.end(STARTED_AT.plusSeconds(600));

        assertThatThrownBy(() -> session.end(STARTED_AT.plusSeconds(700)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SESSION_ALREADY_ENDED));
    }

    private Session startedSession() {
        return Session.restore(
                5L,
                1L,
                SessionType.PERSONAL,
                List.of(10L, 11L),
                1500,
                null,
                STARTED_AT,
                null,
                SessionStatus.IN_PROGRESS
        );
    }
}
