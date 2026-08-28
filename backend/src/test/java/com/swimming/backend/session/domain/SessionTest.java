package com.swimming.backend.session.domain;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionTest {

    private static final Instant STARTED_AT = Instant.parse("2026-08-20T00:00:00Z");

    @Test
    @DisplayName("개인 세션을 진행 상태로 생성한다")
    void startsPersonalSession() {
        Session session = Session.startPersonal(1L, 20L, List.of(10L, 11L), 1500);

        assertThat(session.getId()).isNull();
        assertThat(session.getUserId()).isEqualTo(1L);
        assertThat(session.getType()).isEqualTo(SessionType.PERSONAL);
        assertThat(session.getPlaceId()).isEqualTo(20L);
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
        Session session = Session.startPersonal(1L, 20L, taskIds, 1500);

        taskIds.add(12L);

        assertThat(session.getTaskIds()).containsExactly(10L, 11L);
        assertThatThrownBy(() -> session.getTaskIds().add(12L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("계획 시간 전에 종료하면 실제 시간과 중단 상태를 기록한다")
    void endsBeforePlannedDuration() {
        Session session = startedSession();
        Instant endedAt = STARTED_AT.plusSeconds(600);

        session.end(endedAt, null, Map.of());

        assertThat(session.getActualDurationSec()).isEqualTo(600);
        assertThat(session.getEndedAt()).isEqualTo(endedAt);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.INTERRUPTED);
    }

    @Test
    @DisplayName("계획 시간을 채운 뒤 종료해도 같은 종료 상태로 기록한다")
    void endsAfterPlannedDuration() {
        Session session = startedSession();

        session.end(STARTED_AT.plusSeconds(1560), null, Map.of());

        assertThat(session.getActualDurationSec()).isEqualTo(1560);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
    }

    @Test
    @DisplayName("서버 종료 시각이 시작보다 이르면 0초 종료 기록으로 보정한다")
    void clampsEndTimeBeforeStart() {
        Session session = startedSession();

        session.end(STARTED_AT.minusSeconds(1), null, Map.of());

        assertThat(session.getActualDurationSec()).isZero();
        assertThat(session.getEndedAt()).isEqualTo(STARTED_AT);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.INTERRUPTED);
    }

    @Test
    @DisplayName("이미 종료한 세션은 다시 종료할 수 없다")
    void rejectsRepeatedEnd() {
        Session session = startedSession();
        session.end(STARTED_AT.plusSeconds(600), null, Map.of());

        assertThatThrownBy(() -> session.end(STARTED_AT.plusSeconds(700), null, Map.of()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SESSION_ALREADY_ENDED));
    }

    @Test
    @DisplayName("진행 중인 세션은 마지막 음악 URL을 새 값으로 교체한다")
    void replacesMusicUrl() {
        Session session = startedSession();

        session.updateMusicUrl("https://www.youtube.com/watch?v=first");
        session.updateMusicUrl("https://youtu.be/second");

        assertThat(session.getMusicUrl()).isEqualTo("https://youtu.be/second");
    }

    @Test
    @DisplayName("음악 URL 삭제는 세션의 다른 데이터를 유지한다")
    void clearsOnlyMusicUrl() {
        Session session = startedSession();
        session.updateMusicUrl("https://youtu.be/example");

        session.updateMusicUrl(null);

        assertThat(session.getMusicUrl()).isNull();
        assertThat(session.getId()).isEqualTo(5L);
        assertThat(session.getUserId()).isEqualTo(1L);
        assertThat(session.getPlaceId()).isEqualTo(20L);
        assertThat(session.getTaskIds()).containsExactly(10L, 11L);
        assertThat(session.getPlannedDurationSec()).isEqualTo(1500);
        assertThat(session.getStartedAt()).isEqualTo(STARTED_AT);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("종료한 세션의 음악 URL은 변경할 수 없다")
    void rejectsMusicUpdateAfterEnd() {
        Session session = startedSession();
        session.end(STARTED_AT.plusSeconds(600), null, Map.of());

        assertThatThrownBy(() -> session.updateMusicUrl("https://youtu.be/example"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SESSION_NOT_FOUND));
    }

    @Test
    @DisplayName("진행 중인 세션의 계획 시간을 변경한다")
    void updatesPlannedDuration() {
        Session session = startedSession();

        session.updatePlannedDuration(1800);

        assertThat(session.getPlannedDurationSec()).isEqualTo(1800);
    }

    @Test
    @DisplayName("허용 범위를 벗어난 계획 시간으로 변경할 수 없다")
    void rejectsInvalidPlannedDuration() {
        Session session = startedSession();

        assertThatThrownBy(() -> session.updatePlannedDuration(59))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_SESSION_DURATION));
    }

    @Test
    @DisplayName("종료한 세션의 계획 시간은 변경할 수 없다")
    void rejectsPlannedDurationUpdateAfterEnd() {
        Session session = startedSession();
        session.end(STARTED_AT.plusSeconds(600), null, Map.of());

        assertThatThrownBy(() -> session.updatePlannedDuration(1800))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SESSION_NOT_FOUND));
    }

    private Session startedSession() {
        return Session.restore(
                5L,
                1L,
                SessionType.PERSONAL,
                20L,
                List.of(SessionTask.of(10L), SessionTask.of(11L)),
                null,
                1500,
                null,
                STARTED_AT,
                null,
                SessionStatus.IN_PROGRESS,
                null
        );
    }
}
