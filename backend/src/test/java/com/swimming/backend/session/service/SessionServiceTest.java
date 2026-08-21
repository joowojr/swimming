package com.swimming.backend.session.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.session.domain.Session;
import com.swimming.backend.session.domain.SessionStatus;
import com.swimming.backend.session.domain.SessionType;
import com.swimming.backend.session.repository.SessionRepository;
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
    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(SessionRepository.class);
        sessionService = new SessionService(sessionRepository);
    }

    @Test
    @DisplayName("진행 중인 세션이 없으면 도메인을 엔티티로 변환해 저장한다")
    void savesNewPersonalSession() {
        Session session = Session.startPersonal(1L, List.of(10L, 11L), 1500);
        when(sessionRepository.existsByUserIdAndStatus(1L, SessionStatus.IN_PROGRESS))
                .thenReturn(false);
        when(sessionRepository.saveAndFlush(any(SessionEntity.class)))
                .thenAnswer(invocation -> {
                    SessionEntity entity = invocation.getArgument(0);
                    ReflectionTestUtils.setField(entity, "id", 5L);
                    ReflectionTestUtils.setField(entity, "startedAt", STARTED_AT);
                    return entity;
                });

        Session saved = sessionService.save(session);

        assertThat(saved.getId()).isEqualTo(5L);
        assertThat(saved.getTaskIds()).containsExactly(10L, 11L);
        assertThat(saved.getStartedAt()).isEqualTo(STARTED_AT);
        verify(sessionRepository).saveAndFlush(any(SessionEntity.class));
    }

    @Test
    @DisplayName("사용자에게 진행 중인 세션이 있으면 새 세션 저장을 거부한다")
    void rejectsExistingActiveSession() {
        Session session = Session.startPersonal(1L, List.of(10L, 11L), 1500);
        when(sessionRepository.existsByUserIdAndStatus(1L, SessionStatus.IN_PROGRESS))
                .thenReturn(true);

        assertThatThrownBy(() -> sessionService.save(session))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ACTIVE_SESSION_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("동시 시작으로 활성 사용자 제약이 충돌하면 진행 세션 오류로 변환한다")
    void translatesConcurrentStartConflict() {
        Session session = Session.startPersonal(1L, List.of(10L, 11L), 1500);
        when(sessionRepository.existsByUserIdAndStatus(1L, SessionStatus.IN_PROGRESS))
                .thenReturn(false);
        when(sessionRepository.saveAndFlush(any(SessionEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate active user"));

        assertThatThrownBy(() -> sessionService.save(session))
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
    @DisplayName("현재 사용자의 진행 중인 엔티티를 순수 도메인으로 조회한다")
    void getsActiveSession() {
        SessionEntity entity = startedEntity();
        when(sessionRepository.findByUserIdAndStatus(1L, SessionStatus.IN_PROGRESS))
                .thenReturn(Optional.of(entity));

        assertThat(sessionService.getActive(1L))
                .get()
                .extracting(Session::getId)
                .isEqualTo(5L);
    }

    @Test
    @DisplayName("수정된 도메인을 기존 엔티티에 적용해 저장한다")
    void appliesAndSavesExistingSession() {
        SessionEntity entity = startedEntity();
        Session session = entity.toDomain();
        session.end(STARTED_AT.plusSeconds(600));
        when(sessionRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(entity));
        when(sessionRepository.saveAndFlush(entity)).thenReturn(entity);

        Session saved = sessionService.save(session);

        assertThat(saved.getActualDurationSec()).isEqualTo(600);
        assertThat(saved.getStatus()).isEqualTo(SessionStatus.INTERRUPTED);
        assertThat(entity.getActiveUserId()).isNull();
        verify(sessionRepository).saveAndFlush(entity);
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
                List.of(10L, 11L),
                1500,
                null,
                STARTED_AT,
                null,
                SessionStatus.IN_PROGRESS
        );
        SessionEntity entity = SessionEntity.from(session);
        ReflectionTestUtils.setField(entity, "id", 5L);
        return entity;
    }
}
