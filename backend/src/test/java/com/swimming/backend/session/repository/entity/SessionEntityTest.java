package com.swimming.backend.session.repository.entity;

import com.swimming.backend.session.domain.Session;
import com.swimming.backend.session.domain.SessionStatus;
import com.swimming.backend.session.domain.SessionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionEntityTest {

    private static final Instant STARTED_AT = Instant.parse("2026-08-20T00:00:00Z");

    @Test
    @DisplayName("새 세션 도메인을 활성 사용자 슬롯을 가진 엔티티로 변환한다")
    void createsEntityFromDomain() {
        SessionEntity entity = SessionEntity.from(
                Session.startPersonal(1L, 20L, List.of(10L, 11L), 1500)
        );

        assertThat(entity.getUserId()).isEqualTo(1L);
        assertThat(entity.getActiveUserId()).isEqualTo(1L);
        assertThat(entity.getTaskIds()).containsExactly(10L, 11L);
        assertThat(entity.getPlaceId()).isEqualTo(20L);
        assertThat(entity.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(entity.getStartedAt()).isNull();
    }

    @Test
    @DisplayName("DB가 생성한 시작 시각을 포함해 엔티티를 도메인으로 복원한다")
    void restoresDomainWithDatabaseStartedAt() {
        SessionEntity entity = SessionEntity.from(
                Session.startPersonal(1L, 20L, List.of(10L), 1500)
        );
        ReflectionTestUtils.setField(entity, "id", 5L);
        ReflectionTestUtils.setField(entity, "startedAt", STARTED_AT);

        Session session = entity.toDomain();

        assertThat(session.getId()).isEqualTo(5L);
        assertThat(session.getType()).isEqualTo(SessionType.PERSONAL);
        assertThat(session.getStartedAt()).isEqualTo(STARTED_AT);
    }

    @Test
    @DisplayName("종료된 도메인을 적용하면 종료 기록과 활성 사용자 슬롯을 반영한다")
    void appliesEndedDomain() {
        SessionEntity entity = SessionEntity.from(
                Session.startPersonal(1L, 20L, List.of(10L), 1500)
        );
        ReflectionTestUtils.setField(entity, "id", 5L);
        ReflectionTestUtils.setField(entity, "startedAt", STARTED_AT);
        Session session = entity.toDomain();
        session.end(STARTED_AT.plusSeconds(600));

        entity.apply(session);

        assertThat(entity.getActualDurationSec()).isEqualTo(600);
        assertThat(entity.getEndedAt()).isEqualTo(STARTED_AT.plusSeconds(600));
        assertThat(entity.getStatus()).isEqualTo(SessionStatus.INTERRUPTED);
        assertThat(entity.getActiveUserId()).isNull();
    }

    @Test
    @DisplayName("수정된 마지막 음악 URL을 엔티티에 반영한다")
    void appliesMusicUrl() {
        SessionEntity entity = SessionEntity.from(
                Session.startPersonal(1L, 20L, List.of(10L), 1500)
        );
        ReflectionTestUtils.setField(entity, "id", 5L);
        ReflectionTestUtils.setField(entity, "startedAt", STARTED_AT);
        Session session = entity.toDomain();
        session.updateMusicUrl("https://youtu.be/example");

        entity.apply(session);

        assertThat(entity.getMusicUrl()).isEqualTo("https://youtu.be/example");
    }

    @Test
    @DisplayName("수정된 계획 시간을 엔티티에 반영한다")
    void appliesPlannedDuration() {
        SessionEntity entity = SessionEntity.from(
                Session.startPersonal(1L, 20L, List.of(10L), 1500)
        );
        ReflectionTestUtils.setField(entity, "id", 5L);
        ReflectionTestUtils.setField(entity, "startedAt", STARTED_AT);
        Session session = entity.toDomain();
        session.updatePlannedDuration(1800);

        entity.apply(session);

        assertThat(entity.getPlannedDurationSec()).isEqualTo(1800);
    }
}
