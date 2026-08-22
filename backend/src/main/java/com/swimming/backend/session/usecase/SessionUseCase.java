package com.swimming.backend.session.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.common.util.UrlUtils;
import com.swimming.backend.plan.service.DailyPlanService;
import com.swimming.backend.place.dto.PlaceReference;
import com.swimming.backend.place.service.PlaceService;
import com.swimming.backend.session.domain.Session;
import com.swimming.backend.session.dto.web.ActiveSessionResponse;
import com.swimming.backend.session.dto.web.ActiveSessionTaskResponse;
import com.swimming.backend.session.dto.web.SessionResponse;
import com.swimming.backend.session.dto.web.SessionDetailResponse;
import com.swimming.backend.session.dto.web.StartPersonalSessionRequest;
import com.swimming.backend.session.dto.web.UpdateSessionMusicUrlRequest;
import com.swimming.backend.session.dto.web.UpdateSessionPlannedDurationRequest;
import com.swimming.backend.session.service.SessionService;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final PlaceService placeService;
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
        PlaceReference place = placeService.getReference(request.placeId());

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
    public Optional<ActiveSessionResponse> getActive(Long userId) {
        return sessionService.getActive(userId)
                .map(session -> toActiveResponse(userId, session));
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public SessionDetailResponse get(Long userId, Long sessionId) {
        return toDetailResponse(userId, sessionService.getOwned(userId, sessionId));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public SessionResponse end(Long userId, Long sessionId) {
        Session session = sessionService.getOwned(userId, sessionId);
        session.end(clock.instant());
        Session savedSession = sessionService.save(session);
        PlaceReference place = placeService.getReference(savedSession.getPlaceId());
        return SessionResponse.from(savedSession, place);
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

    private ActiveSessionResponse toActiveResponse(Long userId, Session session) {
        PlaceReference place = placeService.getReference(session.getPlaceId());
        return ActiveSessionResponse.from(session, place, getTasks(userId, session));
    }

    private SessionDetailResponse toDetailResponse(Long userId, Session session) {
        PlaceReference place = placeService.getReference(session.getPlaceId());
        return SessionDetailResponse.from(session, place, getTasks(userId, session));
    }

    private List<ActiveSessionTaskResponse> getTasks(Long userId, Session session) {
        Map<Long, TaskReference> tasksById = taskService
                .getAllByIds(userId, session.getTaskIds())
                .stream()
                .collect(Collectors.toMap(TaskReference::id, Function.identity()));

        if (tasksById.size() != session.getTaskIds().size()) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }

        return session.getTaskIds()
                .stream()
                .map(tasksById::get)
                .map(ActiveSessionTaskResponse::from)
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
