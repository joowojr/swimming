package com.swimming.backend.session.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.plan.service.DailyPlanService;
import com.swimming.backend.place.domain.BackgroundAssetType;
import com.swimming.backend.place.dto.PlaceReference;
import com.swimming.backend.place.service.PlaceVideoService;
import com.swimming.backend.session.domain.Session;
import com.swimming.backend.session.domain.SessionStatus;
import com.swimming.backend.session.domain.SessionTask;
import com.swimming.backend.session.domain.SessionType;
import com.swimming.backend.session.dto.web.EndSessionRequest;
import com.swimming.backend.session.dto.web.SessionResponse;
import com.swimming.backend.session.dto.web.StartPersonalSessionRequest;
import com.swimming.backend.session.dto.web.UpdateSessionMusicUrlRequest;
import com.swimming.backend.session.dto.web.UpdateSessionPlannedDurationRequest;
import com.swimming.backend.session.service.SessionService;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.projection.TaskReference;
import com.swimming.backend.task.service.TaskService;
import com.swimming.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class SessionUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-19T15:30:00Z");

    private SessionService sessionService;
    private DailyPlanService dailyPlanService;
    private UserService userService;
    private TaskService taskService;
    private PlaceVideoService placeVideoService;
    private SessionUseCase sessionUseCase;

    @BeforeEach
    void setUp() {
        sessionService = mock(SessionService.class);
        dailyPlanService = mock(DailyPlanService.class);
        userService = mock(UserService.class);
        taskService = mock(TaskService.class);
        placeVideoService = mock(PlaceVideoService.class);
        sessionUseCase = new SessionUseCase(
                sessionService,
                dailyPlanService,
                userService,
                taskService,
                placeVideoService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("사용자 타임존의 오늘 계획에 담긴 Task로 개인 세션을 시작한다")
    void startsFromTodayPlanInUserTimezone() {
        StartPersonalSessionRequest request = new StartPersonalSessionRequest(
                List.of(10L, 11L), 20L, 1500
        );
        Session session = startedSession(NOW);
        when(userService.getTimezone(1L)).thenReturn("Asia/Seoul");
        when(dailyPlanService.containsAllTasks(
                1L,
                LocalDate.of(2026, 8, 20),
                List.of(10L, 11L)
        )).thenReturn(true);
        when(placeVideoService.getReference(20L)).thenReturn(placeReference());
        when(sessionService.save(any(Session.class))).thenReturn(session);

        SessionResponse response = sessionUseCase.startPersonal(1L, request);

        assertThat(response.taskIds()).containsExactly(10L, 11L);
        assertThat(response.startedAt()).isEqualTo(NOW);
        assertThat(response.place().id()).isEqualTo(20L);
        assertThat(response.status()).isEqualTo(SessionStatus.IN_PROGRESS);
        verify(dailyPlanService).containsAllTasks(
                1L,
                LocalDate.of(2026, 8, 20),
                List.of(10L, 11L)
        );
    }

    @Test
    @DisplayName("개인 세션을 시작하면 대상 Task가 모두 DOING으로 바뀐다")
    void marksSessionTasksAsDoingOnStart() {
        StartPersonalSessionRequest request = new StartPersonalSessionRequest(
                List.of(10L, 11L), 20L, 1500
        );
        when(userService.getTimezone(1L)).thenReturn("Asia/Seoul");
        when(dailyPlanService.containsAllTasks(
                1L,
                LocalDate.of(2026, 8, 20),
                List.of(10L, 11L)
        )).thenReturn(true);
        when(placeVideoService.getReference(20L)).thenReturn(placeReference());
        when(sessionService.save(any(Session.class))).thenReturn(startedSession(NOW));

        sessionUseCase.startPersonal(1L, request);

        verify(taskService).updateStatuses(
                1L,
                Map.of(10L, TaskStatus.DOING, 11L, TaskStatus.DOING)
        );
    }

    @Test
    @DisplayName("오늘 계획에 없는 Task로는 세션을 시작하지 않는다")
    void rejectsTaskOutsideTodayPlan() {
        StartPersonalSessionRequest request = new StartPersonalSessionRequest(
                List.of(10L, 11L), 20L, 1500
        );
        when(userService.getTimezone(1L)).thenReturn("Asia/Seoul");
        when(dailyPlanService.containsAllTasks(
                1L,
                LocalDate.of(2026, 8, 20),
                List.of(10L, 11L)
        )).thenReturn(false);

        assertThatThrownBy(() -> sessionUseCase.startPersonal(1L, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DAILY_PLAN_TASK_NOT_FOUND));
        verify(sessionService, never()).save(any(Session.class));
    }

    @Test
    @DisplayName("소유한 세션을 서버 현재 시각으로 종료한다")
    void endsOwnedSessionAtServerTime() {
        Session session = startedSession(NOW.minusSeconds(600));
        when(sessionService.getOwned(1L, 5L)).thenReturn(session);
        when(sessionService.save(session)).thenReturn(session);
        when(placeVideoService.getReference(20L)).thenReturn(placeReference());

        SessionResponse response = sessionUseCase.end(1L, 5L, null);

        assertThat(response.actualDurationSec()).isEqualTo(600);
        assertThat(response.endedAt()).isEqualTo(NOW);
        assertThat(response.status()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(session.getSummary()).isNull();
        verify(sessionService).save(session);
        verify(taskService, never()).updateStatuses(any(), any());
    }

    @Test
    @DisplayName("기록과 함께 종료하면 기록을 저장하고 Task 상태를 전이한다")
    void endsWithRecordAndTransitionsTasks() {
        Session session = startedSession(NOW.minusSeconds(600));
        when(sessionService.getOwned(1L, 5L)).thenReturn(session);
        when(sessionService.save(session)).thenReturn(session);
        when(placeVideoService.getReference(20L)).thenReturn(placeReference());

        EndSessionRequest request = new EndSessionRequest(
                "1페이지 완료",
                List.of(
                        new EndSessionRequest.TaskResult(10L, true),
                        new EndSessionRequest.TaskResult(11L, false)
                )
        );

        sessionUseCase.end(1L, 5L, request);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(session.getSummary()).isEqualTo("1페이지 완료");
        verify(taskService).updateStatuses(
                1L,
                Map.of(10L, TaskStatus.DONE, 11L, TaskStatus.DOING)
        );
    }

    @Test
    @DisplayName("세션에 없는 Task를 기록하면 거부한다")
    void rejectsRecordWithUnknownTask() {
        Session session = startedSession(NOW.minusSeconds(600));
        when(sessionService.getOwned(1L, 5L)).thenReturn(session);

        EndSessionRequest request = new EndSessionRequest(
                null,
                List.of(new EndSessionRequest.TaskResult(99L, true))
        );

        assertThatThrownBy(() -> sessionUseCase.end(1L, 5L, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_SESSION_TASKS));
        verify(sessionService, never()).save(any(Session.class));
    }

    @Test
    @DisplayName("진행 중인 세션의 Task 표시 정보를 요청 순서대로 반환한다")
    void getsActiveSessionWithOrderedTasks() {
        Session session = startedSession(NOW);
        when(sessionService.getActive(1L)).thenReturn(Optional.of(session));
        when(placeVideoService.getReference(20L)).thenReturn(placeReference());
        when(taskService.getReferences(1L, List.of(10L, 11L))).thenReturn(List.of(
                new TaskReference(11L, 2L, "프로젝트", "다음 Task", null, 0),
                new TaskReference(10L, 2L, "프로젝트", "첫 Task", null, 0)
        ));

        var response = sessionUseCase.getActive(1L).orElseThrow();

        assertThat(response.tasks()).extracting(task -> task.id())
                .containsExactly(10L, 11L);
        assertThat(response.tasks().getFirst().title()).isEqualTo("첫 Task");
    }

    @Test
    @DisplayName("중복 Task 목록으로는 세션을 시작하지 않는다")
    void rejectsDuplicateTasks() {
        StartPersonalSessionRequest request = new StartPersonalSessionRequest(
                List.of(10L, 10L),
                20L,
                1500
        );
        when(userService.getTimezone(1L)).thenReturn("Asia/Seoul");

        assertThatThrownBy(() -> sessionUseCase.startPersonal(1L, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_SESSION_TASKS));
        verify(sessionService, never()).save(any(Session.class));
    }

    @Test
    @DisplayName("존재하지 않는 공간으로는 세션을 시작하지 않는다")
    void rejectsMissingPlace() {
        StartPersonalSessionRequest request = new StartPersonalSessionRequest(
                List.of(10L, 11L), 99L, 1500
        );
        when(userService.getTimezone(1L)).thenReturn("Asia/Seoul");
        when(dailyPlanService.containsAllTasks(
                1L,
                LocalDate.of(2026, 8, 20),
                List.of(10L, 11L)
        )).thenReturn(true);
        when(placeVideoService.getReference(99L))
                .thenThrow(new BusinessException(ErrorCode.PLACE_NOT_FOUND));

        assertThatThrownBy(() -> sessionUseCase.startPersonal(1L, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PLACE_NOT_FOUND));
        verify(sessionService, never()).save(any(Session.class));
    }

    @Test
    @DisplayName("세션의 마지막 YouTube URL을 저장한다")
    void updatesMusicUrl() {
        Session session = startedSession(NOW);
        when(sessionService.getOwned(1L, 5L)).thenReturn(session);
        when(sessionService.save(session)).thenReturn(session);

        sessionUseCase.updateMusicUrl(
                1L,
                5L,
                new UpdateSessionMusicUrlRequest("https://www.youtube.com/playlist?list=example")
        );

        assertThat(session.getMusicUrl())
                .isEqualTo("https://www.youtube.com/playlist?list=example");
        verify(sessionService).save(session);
    }

    @Test
    @DisplayName("YouTube가 아닌 음악 URL은 저장하지 않는다")
    void rejectsUnsupportedMusicUrl() {
        assertThatThrownBy(() -> sessionUseCase.updateMusicUrl(
                1L,
                5L,
                new UpdateSessionMusicUrlRequest("https://example.com/music")
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_MUSIC_URL));

        verify(sessionService, never()).getOwned(1L, 5L);
    }

    @Test
    @DisplayName("영상이나 재생목록을 가리키지 않는 YouTube URL은 저장하지 않는다")
    void rejectsYouTubeUrlWithoutPlayableTarget() {
        assertThatThrownBy(() -> sessionUseCase.updateMusicUrl(
                1L,
                5L,
                new UpdateSessionMusicUrlRequest("https://www.youtube.com/watch?v=")
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_MUSIC_URL));

        verify(sessionService, never()).getOwned(1L, 5L);
    }

    @Test
    @DisplayName("진행 중인 세션의 계획 시간을 저장한다")
    void updatesPlannedDuration() {
        Session session = startedSession(NOW);
        when(sessionService.getOwned(1L, 5L)).thenReturn(session);
        when(sessionService.save(session)).thenReturn(session);

        sessionUseCase.updatePlannedDuration(
                1L,
                5L,
                new UpdateSessionPlannedDurationRequest(1800)
        );

        assertThat(session.getPlannedDurationSec()).isEqualTo(1800);
        verify(sessionService).save(session);
    }

    private Session startedSession(Instant startedAt) {
        return Session.restore(
                5L,
                1L,
                SessionType.PERSONAL,
                20L,
                List.of(SessionTask.of(10L), SessionTask.of(11L)),
                null,
                1500,
                null,
                startedAt,
                null,
                SessionStatus.IN_PROGRESS,
                null
        );
    }

    private PlaceReference placeReference() {
        return new PlaceReference(
                20L,
                3L,
                "Lisbon",
                "Alfama Cafe",
                BackgroundAssetType.VIDEO,
                "places/video/alfama.mp4",
                "https://bucket.s3.amazonaws.com/alfama.mp4?X-Amz-Signature=abc",
                "https://youtu.be/default"
        );
    }
}
