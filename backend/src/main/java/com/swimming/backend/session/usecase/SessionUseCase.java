package com.swimming.backend.session.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.common.util.UrlUtils;
import com.swimming.backend.plan.service.DailyPlanService;
import com.swimming.backend.place.dto.PlaceReference;
import com.swimming.backend.place.service.PlaceVideoService;
import com.swimming.backend.session.domain.Session;
import com.swimming.backend.session.dto.web.SessionTaskResponse;
import com.swimming.backend.session.dto.web.EndSessionRequest;
import com.swimming.backend.session.dto.web.SessionResponse;
import com.swimming.backend.session.dto.web.SessionDetailResponse;
import com.swimming.backend.session.dto.web.StartPersonalSessionRequest;
import com.swimming.backend.session.dto.web.UpdateSessionMusicUrlRequest;
import com.swimming.backend.session.dto.web.UpdateSessionPlannedDurationRequest;
import com.swimming.backend.session.service.SessionService;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.projection.TaskReference;
import com.swimming.backend.task.service.TaskService;
import com.swimming.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionUseCase {

    private static final String YOUTUBE_DOMAIN = "youtube.com";
    private static final String YOUTUBE_SHORT_DOMAIN = "youtu.be";
    private static final String YOUTUBE_NO_COOKIE_DOMAIN = "youtube-nocookie.com";

    private final SessionService sessionService;
    private final DailyPlanService dailyPlanService;
    private final UserService userService;
    private final TaskService taskService;
    private final PlaceVideoService placeVideoService;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRED)
    public SessionResponse startPersonal(
            Long userId,
            StartPersonalSessionRequest request
    ) {
        Instant startedAt = clock.instant();
        ZoneId timezone = ZoneId.of(userService.getTimezone(userId));
        LocalDate today = LocalDate.ofInstant(startedAt, timezone);
        List<Long> taskIds = request.taskIds();

        if (taskIds.isEmpty() || new HashSet<>(taskIds).size() != taskIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_SESSION_TASKS);
        }
        if (!dailyPlanService.containsAllTasks(userId, today, taskIds)) {
            throw new BusinessException(ErrorCode.DAILY_PLAN_TASK_NOT_FOUND);
        }
        PlaceReference place = placeVideoService.getReference(request.placeId());

        taskService.updateStatuses(userId, taskIds.stream()
                .collect(Collectors.toMap(Function.identity(), taskId -> TaskStatus.DOING)));

        Session session = Session.startPersonal(
                userId,
                place.id(),
                taskIds,
                request.plannedDurationSec()
        );
        return SessionResponse.from(sessionService.save(session), place);
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public Optional<SessionDetailResponse> getActive(Long userId) {
        return sessionService.getActive(userId)
                .map(session -> toDetailResponse(userId, session));
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<SessionDetailResponse> getAll(Long userId) {
        return sessionService.getOwnedSessions(userId)
                .stream()
                .map(session -> toDetailResponse(userId, session))
                .toList();
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public SessionDetailResponse get(Long userId, Long sessionId) {
        return toDetailResponse(userId, sessionService.getOwned(userId, sessionId));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public SessionResponse end(Long userId, Long sessionId, EndSessionRequest request) {
        Session session = sessionService.getOwned(userId, sessionId);
        Map<Long, Boolean> completionByTaskId = toCompletionByTaskId(session, request);

        Instant endTime = request != null && request.usePlannedDuration()
                ? session.getStartedAt().plusSeconds(session.getPlannedDurationSec())
                : clock.instant();
        session.end(endTime, toSummary(request));

        if (!completionByTaskId.isEmpty()) {
            taskService.updateStatuses(userId, toStatusByTaskId(completionByTaskId));
        }

        Session savedSession = sessionService.save(session);
        PlaceReference place = placeVideoService.getReference(savedSession.getPlaceId());
        return SessionResponse.from(savedSession, place);
    }

    private String toSummary(EndSessionRequest request) {
        if (request == null || request.summary() == null || request.summary().isBlank()) {
            return null;
        }
        return request.summary();
    }

    private Map<Long, Boolean> toCompletionByTaskId(Session session, EndSessionRequest request) {
        if (request == null || request.taskResults() == null) {
            return Map.of();
        }

        Map<Long, Boolean> completionByTaskId = new LinkedHashMap<>();
        for (EndSessionRequest.TaskResult result : request.taskResults()) {
            if (completionByTaskId.put(result.taskId(), result.isCompleted()) != null) {
                throw new BusinessException(ErrorCode.INVALID_SESSION_TASKS);
            }
        }

        Set<Long> sessionTaskIds = new HashSet<>(session.getTaskIds());
        if (!sessionTaskIds.containsAll(completionByTaskId.keySet())) {
            throw new BusinessException(ErrorCode.INVALID_SESSION_TASKS);
        }
        return completionByTaskId;
    }

    private Map<Long, TaskStatus> toStatusByTaskId(Map<Long, Boolean> completionByTaskId) {
        return completionByTaskId.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue() ? TaskStatus.DONE : TaskStatus.DOING
                ));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateMusicUrl(
            Long userId,
            Long sessionId,
            UpdateSessionMusicUrlRequest request
    ) {
        if (!isValidMusicUrl(request.musicUrl())) {
            throw new BusinessException(ErrorCode.INVALID_MUSIC_URL);
        }

        Session session = sessionService.getOwned(userId, sessionId);
        session.updateMusicUrl(request.musicUrl());
        sessionService.save(session);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updatePlannedDuration(
            Long userId,
            Long sessionId,
            UpdateSessionPlannedDurationRequest request
    ) {
        Session session = sessionService.getOwned(userId, sessionId);
        session.updatePlannedDuration(request.plannedDurationSec());
        sessionService.save(session);
    }

    private SessionDetailResponse toDetailResponse(Long userId, Session session) {
        PlaceReference place = placeVideoService.getReference(session.getPlaceId());
        return SessionDetailResponse.from(session, place, getTasks(userId, session));
    }

    private List<SessionTaskResponse> getTasks(Long userId, Session session) {
        Map<Long, TaskReference> tasksById = taskService
                .getReferences(userId, session.getTaskIds())
                .stream()
                .collect(Collectors.toMap(TaskReference::id, Function.identity()));

        if (tasksById.size() != session.getTaskIds().size()) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }

        return session.getTaskIds()
                .stream()
                .map(tasksById::get)
                .map(SessionTaskResponse::from)
                .toList();
    }

    private boolean isValidMusicUrl(String musicUrl) {
        if (musicUrl == null) {
            return true;
        }
        if (musicUrl.isBlank()) {
            return false;
        }

        return UrlUtils.parseHttpUrl(musicUrl)
                .map(this::isYouTubeVideoOrPlaylistUrl)
                .orElse(false);
    }

    private boolean isYouTubeVideoOrPlaylistUrl(URI uri) {
        String path = uri.getPath();
        if (UrlUtils.hasHostOrSubdomain(uri, YOUTUBE_SHORT_DOMAIN)) {
            return path != null && path.length() > 1;
        }

        boolean youtubeHost = UrlUtils.hasHostOrSubdomain(uri, YOUTUBE_DOMAIN);
        boolean youtubeNoCookieHost = UrlUtils.hasHostOrSubdomain(
                uri,
                YOUTUBE_NO_COOKIE_DOMAIN
        );
        if (!youtubeHost && !youtubeNoCookieHost) {
            return false;
        }

        if (path != null && (path.startsWith("/embed/") || path.startsWith("/shorts/"))) {
            return path.length() > path.indexOf('/', 1) + 1;
        }
        if (!youtubeHost || path == null) {
            return false;
        }
        return (path.equals("/watch") && UrlUtils.hasNonEmptyQueryParameter(uri, "v"))
                || (path.equals("/playlist")
                && UrlUtils.hasNonEmptyQueryParameter(uri, "list"));
    }
}
