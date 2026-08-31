package com.swimming.backend.session.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.session.domain.Session;
import com.swimming.backend.session.domain.SessionStatus;
import com.swimming.backend.session.domain.SessionTask;
import com.swimming.backend.session.domain.SessionType;
import com.swimming.backend.session.repository.SessionRepository;
import com.swimming.backend.session.repository.SessionTaskRepository;
import com.swimming.backend.session.dto.projection.SessionWithPlaceRow;
import com.swimming.backend.place.domain.BackgroundAssetType;
import com.swimming.backend.session.repository.entity.SessionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionServiceTest {

    private static final Instant STARTED_AT = Instant.parse("2026-08-20T00:00:00Z");

    private SessionRepository sessionRepository;
    private SessionTaskRepository sessionTaskRepository;
    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(SessionRepository.class);
        sessionTaskRepository = mock(SessionTaskRepository.class);
        sessionService = new SessionService(
                sessionRepository,
                sessionTaskRepository
        );
    }

    @Test
    @DisplayName("진행 중인 세션이 없으면 도메인을 엔티티로 변환해 저장한다")
    void savesNewPersonalSession() {
        Session session = Session.createPersonal(1L, 20L, List.of(10L, 11L), 1500);
        when(sessionRepository.saveAndFlush(any(SessionEntity.class)))
                .thenAnswer(invocation -> {
                    SessionEntity entity = invocation.getArgument(0);
                    ReflectionTestUtils.setField(entity, "id", 5L);
                    ReflectionTestUtils.setField(entity, "startedAt", STARTED_AT);
                    return entity;
                });

        Session saved = sessionService.create(session);

        assertThat(saved.getId()).isEqualTo(5L);
        assertThat(saved.getTaskIds()).containsExactly(10L, 11L);
        assertThat(saved.getPlaceId()).isEqualTo(20L);
        assertThat(saved.getStartedAt()).isEqualTo(STARTED_AT);
        verify(sessionRepository).saveAndFlush(any(SessionEntity.class));
    }

    @Test
    @DisplayName("활성 사용자 제약이 충돌하면 진행 세션 오류로 변환한다")
    void translatesConcurrentStartConflict() {
        Session session = Session.createPersonal(1L, 20L, List.of(10L, 11L), 1500);
        when(sessionRepository.saveAndFlush(any(SessionEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate active user"));

        assertThatThrownBy(() -> sessionService.create(session))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ACTIVE_SESSION_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("사용자가 소유한 엔티티를 순수 도메인으로 조회한다")
    void getsOwnedSession() {
        SessionEntity entity = startedEntity();
        when(sessionRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(entity));

        Session session = sessionService.getOwned(1L, 5L);

        assertThat(session.getId()).isEqualTo(5L);
        assertThat(session.getStartedAt()).isEqualTo(STARTED_AT);
    }

    @Test
    @DisplayName("현재 사용자의 진행 중인 세션과 공간을 projection으로 조회한다")
    void getsActiveSession() {
        when(sessionRepository.findActiveRows(1L, SessionStatus.IN_PROGRESS))
                .thenReturn(List.of(sessionWithPlaceRow(10L)));

        assertThat(sessionService.getActiveRows(1L))
                .extracting(SessionWithPlaceRow::sessionId)
                .singleElement()
                .isEqualTo(5L);
    }

    @Test
    @DisplayName("사용자가 소유한 세션과 공간을 projection으로 조회한다")
    void getsOwnedSessionWithPlace() {
        when(sessionRepository.findOwnedRows(1L, 5L))
                .thenReturn(List.of(sessionWithPlaceRow(10L)));

        List<SessionWithPlaceRow> result = sessionService.getOwnedRows(1L, 5L);

        assertThat(result.getFirst().sessionId()).isEqualTo(5L);
        assertThat(result.getFirst().placeName()).isEqualTo("Alfama Cafe");
    }

    @Test
    @DisplayName("사용자의 세션을 최신 시작 시각 순서로 조회한다")
    void getsOwnedSessionsInLatestOrder() {
        when(sessionRepository.findListRows(1L)).thenReturn(List.of(sessionWithPlaceRow(10L)));

        assertThat(sessionService.getOwnedRows(1L))
                .extracting(SessionWithPlaceRow::sessionId)
                .containsExactly(5L);
    }

    @Test
    @DisplayName("종료된 Session 도메인을 Entity와 완료 Task에 반영한다")
    void savesEndedSessionStateAndTaskResults() {
        SessionEntity entity = startedEntity();
        Session session = entity.toDomain();
        session.end(STARTED_AT.plusSeconds(600), null, List.of(10L));
        when(sessionRepository.findById(5L)).thenReturn(Optional.of(entity));
        when(sessionTaskRepository.completeAll(5L, List.of(10L))).thenReturn(1);

        Session saved = sessionService.updateEnd(session, List.of(10L));

        assertThat(saved.getActualDurationSec()).isEqualTo(600);
        assertThat(saved.getStatus()).isEqualTo(SessionStatus.INTERRUPTED);
        assertThat(entity.getStatus()).isEqualTo(SessionStatus.INTERRUPTED);
        assertThat(entity.getActiveUserId()).isNull();
        verify(sessionRepository).findById(5L);
        verify(sessionTaskRepository).completeAll(5L, List.of(10L));
    }

    @Test
    @DisplayName("완료한 Task가 없으면 완료 처리 JPQL을 실행하지 않는다")
    void skipsCompletionUpdateWithoutCompletedTasks() {
        SessionEntity entity = startedEntity();
        Session session = entity.toDomain();
        session.end(STARTED_AT.plusSeconds(600), null, List.of());
        when(sessionRepository.findById(5L)).thenReturn(Optional.of(entity));

        sessionService.updateEnd(session, List.of());

        verify(sessionTaskRepository, org.mockito.Mockito.never())
                .completeAll(any(), any());
    }

    @Test
    @DisplayName("음악 URL API는 소유 Entity의 음악 URL만 변경 감지로 저장한다")
    void updatesOnlyMusicUrl() {
        SessionEntity entity = startedEntity();
        when(sessionRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(entity));

        sessionService.updateMusicUrl(1L, 5L, "https://youtu.be/example");

        assertThat(entity.getMusicUrl()).isEqualTo("https://youtu.be/example");
        assertThat(entity.getPlannedDurationSec()).isEqualTo(1500);
        verify(sessionRepository).flush();
        verify(sessionTaskRepository, org.mockito.Mockito.never())
                .completeAll(any(), any());
    }

    @Test
    @DisplayName("계획 시간 API는 소유 Entity의 계획 시간만 변경 감지로 저장한다")
    void updatesOnlyPlannedDuration() {
        SessionEntity entity = startedEntity();
        when(sessionRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(entity));

        sessionService.updatePlannedDuration(1L, 5L, 1800);

        assertThat(entity.getPlannedDurationSec()).isEqualTo(1800);
        assertThat(entity.getMusicUrl()).isNull();
        verify(sessionRepository).flush();
        verify(sessionTaskRepository, org.mockito.Mockito.never())
                .completeAll(any(), any());
    }

    @Test
    @DisplayName("존재하지 않거나 다른 사용자의 세션은 찾을 수 없다")
    void rejectsMissingOrUnownedSession() {
        when(sessionRepository.findByIdAndUserId(5L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.getOwned(2L, 5L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SESSION_NOT_FOUND));
    }

    private SessionEntity startedEntity() {
        Session session = Session.restore(
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
        SessionEntity entity = SessionEntity.from(session);
        ReflectionTestUtils.setField(entity, "id", 5L);
        return entity;
    }

    private SessionWithPlaceRow sessionWithPlaceRow(Long taskId) {
        return new SessionWithPlaceRow(
                5L, 1L, SessionType.PERSONAL, 20L, taskId, null, null,
                1500, null, STARTED_AT, null, SessionStatus.IN_PROGRESS, null,
                3L, "Lisbon", "PT", "Europe/Lisbon", "Alfama Cafe", BackgroundAssetType.VIDEO,
                "places/video/alfama.mp4", null
        );
    }
}
