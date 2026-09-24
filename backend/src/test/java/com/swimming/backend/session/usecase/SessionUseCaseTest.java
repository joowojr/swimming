package com.swimming.backend.session.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.place.domain.BackgroundAssetType;
import com.swimming.backend.place.domain.City;
import com.swimming.backend.place.domain.Place;
import com.swimming.backend.place.service.PlaceService;
import com.swimming.backend.place.service.PlaceVideoService;
import com.swimming.backend.session.domain.Session;
import com.swimming.backend.session.domain.SessionStatus;
import com.swimming.backend.session.domain.SessionTask;
import com.swimming.backend.session.domain.SessionType;
import com.swimming.backend.session.dto.projection.SessionWithPlaceRow;
import com.swimming.backend.session.dto.web.EndSessionRequest;
import com.swimming.backend.session.dto.web.SessionResponse;
import com.swimming.backend.session.dto.web.SessionDetailResponse;
import com.swimming.backend.session.dto.web.SessionTaskResponse;
import com.swimming.backend.session.dto.web.StartPersonalSessionRequest;
import com.swimming.backend.session.dto.web.UpdateSessionMusicUrlRequest;
import com.swimming.backend.session.dto.web.UpdateSessionFocusDurationRequest;
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
    private UserService userService;
    private TaskService taskService;
    private PlaceService placeService;
    private PlaceVideoService placeVideoService;
    private SessionUseCase sessionUseCase;

    @BeforeEach
    void setUp() {
        sessionService = mock(SessionService.class);
        userService = mock(UserService.class);
        taskService = mock(TaskService.class);
        placeService = mock(PlaceService.class);
        placeVideoService = mock(PlaceVideoService.class);
        sessionUseCase = new SessionUseCase(
                sessionService,
                userService,
                taskService,
                placeService,
                placeVideoService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("사용자 타임존의 오늘 계획에 담긴 Task로 개인 세션을 시작한다")
    void startsFromTodayPlanInUserTimezone() {
        StartPersonalSessionRequest request = new StartPersonalSessionRequest(
                List.of(10L, 11L), 20L, 1500, 1500, 0, 1
        );
        Session session = startedSession(NOW);
        when(userService.getTimezone(1L)).thenReturn("Asia/Seoul");
        when(taskService.countPlannedOn(
                1L,
                LocalDate.of(2026, 8, 20),
                List.of(10L, 11L)
        )).thenReturn(2L);
        Place place = place();
        when(placeService.getOne(20L)).thenReturn(place);
        when(sessionService.create(any(Session.class))).thenReturn(session);
        when(sessionService.getOwnedRows(1L, 5L)).thenReturn(List.of(
                sessionWithPlaceRow(session, place, 10L, false),
                sessionWithPlaceRow(session, place, 11L, false)
        ));
        when(placeVideoService.resolveBackgroundUrl("places/video/alfama.mp4"))
                .thenReturn("https://cdn.example.com/alfama.mp4");
        when(placeVideoService.resolveThumbnailUrl("places/thumbnails/alfama.mp4"))
                .thenReturn("https://cdn.example.com/places/thumbnails/alfama.mp4");
        when(taskService.getReferences(1L, List.of(10L, 11L))).thenReturn(List.of(
                new TaskReference(10L, 2L, "폴더", "첫 Task", null),
                new TaskReference(11L, 2L, "폴더", "다음 Task", null)
        ));

        SessionDetailResponse response = sessionUseCase.startPersonal(1L, request);

        assertThat(response.tasks()).extracting(SessionTaskResponse::id)
                .containsExactly(10L, 11L);
        assertThat(response.tasks().getFirst().title()).isEqualTo("첫 Task");
        assertThat(response.startedAt()).isEqualTo(NOW);
        assertThat(response.place().id()).isEqualTo(20L);
        assertThat(response.place().backgroundAsset().url())
                .isEqualTo("https://cdn.example.com/alfama.mp4");
        assertThat(response.place().backgroundAsset().thumbnailUrl())
                .isEqualTo("https://cdn.example.com/places/thumbnails/alfama.mp4");
        assertThat(response.status()).isEqualTo(SessionStatus.IN_PROGRESS);
        verify(taskService).countPlannedOn(
                1L,
                LocalDate.of(2026, 8, 20),
                List.of(10L, 11L)
        );
    }

    @Test
    @DisplayName("개인 세션 시작은 Task 상태를 바꾸지 않는다")
    void keepsTaskStatusOnStart() {
        StartPersonalSessionRequest request = new StartPersonalSessionRequest(
                List.of(10L, 11L), 20L, 1500, 1500, 0, 1
        );
        when(userService.getTimezone(1L)).thenReturn("Asia/Seoul");
        when(taskService.countPlannedOn(
                1L,
                LocalDate.of(2026, 8, 20),
                List.of(10L, 11L)
        )).thenReturn(2L);
        Session session = startedSession(NOW);
        Place place = place();
        when(placeService.getOne(20L)).thenReturn(place);
        when(sessionService.create(any(Session.class))).thenReturn(session);
        when(sessionService.getOwnedRows(1L, 5L)).thenReturn(List.of(
                sessionWithPlaceRow(session, place, 10L, false),
                sessionWithPlaceRow(session, place, 11L, false)
        ));
        when(taskService.getReferences(1L, List.of(10L, 11L))).thenReturn(List.of(
                new TaskReference(10L, 2L, "폴더", "첫 Task", null),
                new TaskReference(11L, 2L, "폴더", "다음 Task", null)
        ));

        sessionUseCase.startPersonal(1L, request);

        verify(taskService, never()).updateStatuses(any(), any());
    }

    @Test
    @DisplayName("오늘 계획에 없는 Task로는 세션을 시작하지 않는다")
    void rejectsTaskOutsideTodayPlan() {
        StartPersonalSessionRequest request = new StartPersonalSessionRequest(
                List.of(10L, 11L), 20L, 1500, 1500, 0, 1
        );
        when(userService.getTimezone(1L)).thenReturn("Asia/Seoul");
        when(taskService.countPlannedOn(
                1L,
                LocalDate.of(2026, 8, 20),
                List.of(10L, 11L)
        )).thenReturn(1L);

        assertThatThrownBy(() -> sessionUseCase.startPersonal(1L, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DAILY_PLAN_TASK_NOT_FOUND));
        verify(sessionService, never()).create(any(Session.class));
    }

    @Test
    @DisplayName("소유한 세션을 서버 현재 시각으로 종료한다")
    void endsOwnedSessionAtServerTime() {
        Session session = startedSession(NOW.minusSeconds(600));
        when(sessionService.getOwned(1L, 5L)).thenReturn(session);
        when(sessionService.updateEnd(session, List.of())).thenReturn(session);
        when(placeService.getOne(20L)).thenReturn(place());

        SessionResponse response = sessionUseCase.end(1L, 5L, null);

        assertThat(response.actualDurationSec()).isEqualTo(600);
        assertThat(response.endedAt()).isEqualTo(NOW);
        assertThat(response.status()).isEqualTo(SessionStatus.INTERRUPTED);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.INTERRUPTED);
        assertThat(session.getSummary()).isNull();
        verify(sessionService).updateEnd(session, List.of());
        verify(taskService, never()).updateStatuses(any(), any());
    }

    @Test
    @DisplayName("계획 시간으로 종료하도록 요청하면 계획된 시각과 시간으로 기록한다")
    void endsOwnedSessionAtPlannedTime() {
        Session session = startedSession(NOW.minusSeconds(600));
        when(sessionService.getOwned(1L, 5L)).thenReturn(session);
        when(sessionService.updateEnd(session, List.of())).thenReturn(session);
        when(placeService.getOne(20L)).thenReturn(place());

        SessionResponse response = sessionUseCase.end(
                1L,
                5L,
                new EndSessionRequest(null, true, List.of())
        );

        assertThat(response.actualDurationSec()).isEqualTo(1500);
        assertThat(response.endedAt()).isEqualTo(NOW.plusSeconds(900));
    }

    @Test
    @DisplayName("기록과 함께 종료하면 기록을 저장하고 Task 상태를 전이한다")
    void endsWithRecordAndTransitionsTasks() {
        Session session = startedSession(NOW.minusSeconds(600));
        when(sessionService.getOwned(1L, 5L)).thenReturn(session);
        when(sessionService.updateEnd(session, List.of(10L))).thenReturn(session);
        when(placeService.getOne(20L)).thenReturn(place());

        EndSessionRequest request = new EndSessionRequest(
                "1페이지 완료",
                false,
                List.of(
                        new EndSessionRequest.TaskResult(10L, true),
                        new EndSessionRequest.TaskResult(11L, false)
                )
        );

        sessionUseCase.end(1L, 5L, request);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.INTERRUPTED);
        assertThat(session.getSummary()).isEqualTo("1페이지 완료");
        assertThat(session.getTasks())
                .extracting(SessionTask::isCompleted)
                .containsExactly(true, false);
        verify(taskService).updateStatuses(
                1L,
                Map.of(10L, TaskStatus.DONE, 11L, TaskStatus.DOING)
        );
    }

    @Test
    @DisplayName("세션에 없는 Task를 기록하면 거부한다")
    void rejectsRecordWithUnknownTask() {
        Session session = startedSession(NOW.minusSeconds(600));
        EndSessionRequest request = new EndSessionRequest(
                null,
                false,
                List.of(new EndSessionRequest.TaskResult(99L, true))
        );

        when(sessionService.getOwned(1L, 5L)).thenReturn(session);

        assertThatThrownBy(() -> sessionUseCase.end(1L, 5L, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_SESSION_TASKS));
        verify(taskService, never()).updateStatuses(any(), any());
        verify(sessionService, never()).updateEnd(any(), any());
    }

    @Test
    @DisplayName("진행 중인 세션의 Task 표시 정보를 Session 도메인 순서대로 반환한다")
    void getsActiveSessionWithOrderedTasks() {
        Session session = startedSession(NOW);
        Place place = place();
        when(sessionService.getActiveRows(1L)).thenReturn(List.of(
                sessionWithPlaceRow(session, place, 10L, false),
                sessionWithPlaceRow(session, place, 11L, false)
        ));
        when(placeVideoService.resolveBackgroundUrl("places/video/alfama.mp4"))
                .thenReturn("https://cdn.example.com/alfama.mp4");
        when(taskService.getReferences(1L, List.of(10L, 11L))).thenReturn(List.of(
                new TaskReference(11L, 2L, "폴더", "다음 Task", null),
                new TaskReference(10L, 2L, "폴더", "첫 Task", null)
        ));

        var response = sessionUseCase.getActive(1L).orElseThrow();

        assertThat(response.tasks()).extracting(task -> task.id())
                .containsExactly(10L, 11L);
        assertThat(response.tasks().getFirst().title()).isEqualTo("첫 Task");
        verify(placeService, never()).getOne(any());
    }

    @Test
    @DisplayName("세션 목록의 공간과 Task 표시 정보를 각각 한 번씩 일괄 조회한다")
    void getsAllSessionsWithFixedBatchQueries() {
        Session first = Session.restore(
                5L, 1L, SessionType.PERSONAL, 20L,
                List.of(SessionTask.of(10L), SessionTask.of(11L)),
                null, 1500, null, NOW, null, SessionStatus.IN_PROGRESS, null
        );
        Session second = Session.restore(
                4L, 1L, SessionType.PERSONAL, 21L,
                List.of(SessionTask.of(11L), SessionTask.of(12L)),
                null, 1500, null, NOW.minusSeconds(3600), null, SessionStatus.IN_PROGRESS, null
        );
        Place firstPlace = place(20L, "Alfama Cafe", "places/video/alfama.mp4");
        Place secondPlace = place(21L, "Belem Cafe", "places/video/belem.mp4");
        when(sessionService.getOwnedRows(1L)).thenReturn(List.of(
                sessionWithPlaceRow(first, firstPlace, 10L, false),
                sessionWithPlaceRow(first, firstPlace, 11L, false),
                sessionWithPlaceRow(second, secondPlace, 11L, false),
                sessionWithPlaceRow(second, secondPlace, 12L, false)
        ));
        when(placeVideoService.resolveBackgroundUrl("places/video/alfama.mp4"))
                .thenReturn("https://cdn.example.com/alfama.mp4");
        when(placeVideoService.resolveBackgroundUrl("places/video/belem.mp4"))
                .thenReturn("https://cdn.example.com/belem.mp4");
        when(taskService.getReferences(1L, List.of(10L, 11L, 12L))).thenReturn(List.of(
                new TaskReference(12L, 2L, "폴더", "세 번째 Task", TaskStatus.TODO),
                new TaskReference(10L, 2L, "폴더", "첫 Task", TaskStatus.DOING),
                new TaskReference(11L, 2L, "폴더", "두 번째 Task", TaskStatus.DONE)
        ));

        var responses = sessionUseCase.getAll(1L);

        assertThat(responses).hasSize(2);
        assertThat(responses.getFirst().tasks()).extracting(task -> task.id())
                .containsExactly(10L, 11L);
        assertThat(responses.getLast().tasks()).extracting(task -> task.id())
                .containsExactly(11L, 12L);
        verify(placeVideoService).resolveBackgroundUrl("places/video/alfama.mp4");
        verify(placeVideoService).resolveBackgroundUrl("places/video/belem.mp4");
        verify(taskService).getReferences(1L, List.of(10L, 11L, 12L));
    }

    @Test
    @DisplayName("중복 Task 목록으로는 세션을 시작하지 않는다")
    void rejectsDuplicateTasks() {
        StartPersonalSessionRequest request = new StartPersonalSessionRequest(
                List.of(10L, 10L),
                20L,
                1500,
                1500,
                0,
                1
        );
        when(userService.getTimezone(1L)).thenReturn("Asia/Seoul");

        assertThatThrownBy(() -> sessionUseCase.startPersonal(1L, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_SESSION_TASKS));
        verify(sessionService, never()).create(any(Session.class));
    }

    @Test
    @DisplayName("존재하지 않는 공간으로는 세션을 시작하지 않는다")
    void rejectsMissingPlace() {
        StartPersonalSessionRequest request = new StartPersonalSessionRequest(
                List.of(10L, 11L), 99L, 1500, 1500, 0, 1
        );
        when(userService.getTimezone(1L)).thenReturn("Asia/Seoul");
        when(taskService.countPlannedOn(
                1L,
                LocalDate.of(2026, 8, 20),
                List.of(10L, 11L)
        )).thenReturn(2L);
        when(placeService.getOne(99L))
                .thenThrow(new BusinessException(ErrorCode.PLACE_NOT_FOUND));

        assertThatThrownBy(() -> sessionUseCase.startPersonal(1L, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PLACE_NOT_FOUND));
        verify(sessionService, never()).create(any(Session.class));
    }

    @Test
    @DisplayName("세션의 마지막 YouTube URL을 저장한다")
    void updatesMusicUrl() {
        sessionUseCase.updateMusicUrl(
                1L,
                5L,
                new UpdateSessionMusicUrlRequest("https://www.youtube.com/playlist?list=example")
        );

        verify(sessionService).updateMusicUrl(
                1L,
                5L,
                "https://www.youtube.com/playlist?list=example"
        );
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

        verify(sessionService, never()).updateMusicUrl(any(), any(), any());
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

        verify(sessionService, never()).updateMusicUrl(any(), any(), any());
    }

    @Test
    @DisplayName("진행 중인 세션의 집중 시간과 파생 계획 시간을 저장한다")
    void updatesFocusDuration() {
        Session session = startedSession(NOW);
        when(sessionService.getOwned(1L, 5L)).thenReturn(session);

        sessionUseCase.updateFocusDuration(
                1L,
                5L,
                new UpdateSessionFocusDurationRequest(1800)
        );

        assertThat(session.getFocusDurationSec()).isEqualTo(1800);
        assertThat(session.getPlannedDurationSec()).isEqualTo(1800);
        verify(sessionService).updateFocusDuration(session);
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

    private Place place() {
        return place(20L, "Alfama Cafe", "places/video/alfama.mp4");
    }

    private Place place(Long id, String name, String backgroundAssetKey) {
        return Place.restore(
                id,
                City.restore(3L, "Lisbon", "PT", "Europe/Lisbon"),
                name,
                BackgroundAssetType.VIDEO,
                backgroundAssetKey,
                "places/thumbnails/alfama.mp4",
                "https://youtu.be/default"
        );
    }

    private SessionWithPlaceRow sessionWithPlaceRow(
            Session session,
            Place place,
            Long taskId,
            boolean taskCompleted
    ) {
        return new SessionWithPlaceRow(
                session.getId(),
                session.getUserId(),
                session.getType(),
                place.getId(),
                taskId,
                taskCompleted,
                session.getMusicUrl(),
                session.getPlannedDurationSec(),
                session.getFocusDurationSec(),
                session.getBreakDurationSec(),
                session.getRepeatCount(),
                session.getActualDurationSec(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getStatus(),
                session.getSummary(),
                place.getCity().getId(),
                place.getCity().getName(),
                place.getCity().getCountryCode(),
                place.getCity().getTimezone(),
                place.getName(),
                place.getBackgroundAssetType(),
                place.getBackgroundAssetKey(),
                place.getThumbnailAssetKey(),
                place.getDefaultMusicUrl()
        );
    }
}
