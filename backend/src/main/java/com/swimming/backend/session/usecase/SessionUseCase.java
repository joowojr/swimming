package com.swimming.backend.session.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.plan.service.DailyPlanService;
import com.swimming.backend.place.domain.Place;
import com.swimming.backend.place.service.PlaceService;
import com.swimming.backend.place.service.PlaceVideoService;
import com.swimming.backend.session.domain.Session;
import com.swimming.backend.session.dto.projection.SessionWithPlaceRow;
import com.swimming.backend.session.dto.web.SessionTaskResponse;
import com.swimming.backend.session.dto.web.EndSessionRequest;
import com.swimming.backend.session.dto.web.SessionResponse;
import com.swimming.backend.session.dto.web.SessionDetailResponse;
import com.swimming.backend.session.dto.web.StartPersonalSessionRequest;
import com.swimming.backend.session.dto.web.UpdateSessionMusicUrlRequest;
import com.swimming.backend.session.dto.web.UpdateSessionPlannedDurationRequest;
import com.swimming.backend.session.service.SessionService;
import com.swimming.backend.session.validator.SessionMusicUrlValidator;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.projection.TaskReference;
import com.swimming.backend.task.service.TaskService;
import com.swimming.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionUseCase {

    private final SessionService sessionService;
    private final DailyPlanService dailyPlanService;
    private final UserService userService;
    private final TaskService taskService;
    private final PlaceService placeService;
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
        Place place = placeService.getOne(request.placeId());

        taskService.updateStatuses(userId, taskIds.stream()
                .collect(Collectors.toMap(Function.identity(), taskId -> TaskStatus.DOING)));

        Session session = Session.createPersonal(
                userId,
                place.getId(),
                taskIds,
                request.plannedDurationSec()
        );
        return SessionResponse.from(sessionService.create(session), place);
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public Optional<SessionDetailResponse> getActive(Long userId) {
        List<SessionWithPlaceRow> rows = sessionService.getActiveRows(userId);
        return rows.isEmpty()
                ? Optional.empty()
                : Optional.of(toDetailResponse(userId, rows));
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<SessionDetailResponse> getAll(Long userId) {
        List<SessionWithPlaceRow> rows = sessionService.getOwnedRows(userId);
        if (rows.isEmpty()) {
            return List.of();
        }

        List<Long> taskIds = rows.stream()
                .map(SessionWithPlaceRow::taskId)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
        Map<Long, TaskReference> tasksById = taskIds.isEmpty()
                ? Map.of()
                : taskService.getReferences(userId, taskIds).stream()
                .collect(Collectors.toMap(TaskReference::id, Function.identity()));

        if (tasksById.size() != taskIds.size()) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }

        return rows.stream()
                .collect(Collectors.groupingBy(
                        SessionWithPlaceRow::sessionId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ))
                .values()
                .stream()
                .map(sessionRows -> SessionDetailResponse.from(
                        sessionRows.getFirst(),
                        placeVideoService.resolveBackgroundUrl(
                                sessionRows.getFirst().backgroundAssetKey()
                        ),
                        getTasks(sessionRows, tasksById)
                ))
                .toList();
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public SessionDetailResponse get(Long userId, Long sessionId) {
        return toDetailResponse(
                userId,
                sessionService.getOwnedRows(userId, sessionId)
        );
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public SessionResponse end(Long userId, Long sessionId, EndSessionRequest request) {
        Map<Long, Boolean> completionByTaskId = toCompletionByTaskId(request);
        List<Long> completedTaskIds = completionByTaskId.entrySet().stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        Session session = sessionService.getOwned(userId, sessionId);
        Instant currentTime = clock.instant();
        Instant endTime = request != null && request.usePlannedDuration()
                ? session.getStartedAt().plusSeconds(session.getPlannedDurationSec())
                : currentTime;
        session.end(endTime, toSummary(request), completedTaskIds);
        Session savedSession = sessionService.updateEnd(session, completedTaskIds);

        if (!completionByTaskId.isEmpty()) {
            taskService.updateStatuses(userId, toStatusByTaskId(completionByTaskId));
        }

        Place place = placeService.getOne(savedSession.getPlaceId());
        return SessionResponse.from(savedSession, place);
    }

    private String toSummary(EndSessionRequest request) {
        if (request == null || request.summary() == null || request.summary().isBlank()) {
            return null;
        }
        return request.summary();
    }

    private Map<Long, Boolean> toCompletionByTaskId(EndSessionRequest request) {
        if (request == null || request.taskResults() == null) {
            return Map.of();
        }

        Map<Long, Boolean> completionByTaskId = new LinkedHashMap<>();
        for (EndSessionRequest.TaskResult result : request.taskResults()) {
            if (completionByTaskId.put(result.taskId(), result.isCompleted()) != null) {
                throw new BusinessException(ErrorCode.INVALID_SESSION_TASKS);
            }
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
        SessionMusicUrlValidator.validate(request.musicUrl());
        sessionService.updateMusicUrl(userId, sessionId, request.musicUrl());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updatePlannedDuration(
            Long userId,
            Long sessionId,
            UpdateSessionPlannedDurationRequest request
    ) {
        sessionService.updatePlannedDuration(
                userId,
                sessionId,
                request.plannedDurationSec()
        );
    }

    private SessionDetailResponse toDetailResponse(
            Long userId,
            List<SessionWithPlaceRow> rows
    ) {
        SessionWithPlaceRow first = rows.getFirst();
        return SessionDetailResponse.from(
                first,
                placeVideoService.resolveBackgroundUrl(first.backgroundAssetKey()),
                getTasks(userId, rows)
        );
    }

    private List<SessionTaskResponse> getTasks(
            Long userId,
            List<SessionWithPlaceRow> rows
    ) {
        List<Long> taskIds = rows.stream()
                .map(SessionWithPlaceRow::taskId)
                .toList();
        Map<Long, TaskReference> tasksById = taskService
                .getReferences(userId, taskIds)
                .stream()
                .collect(Collectors.toMap(TaskReference::id, Function.identity()));

        if (tasksById.size() != taskIds.size()) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }

        return getTasks(rows, tasksById);
    }

    private List<SessionTaskResponse> getTasks(
            List<SessionWithPlaceRow> rows,
            Map<Long, TaskReference> tasksById
    ) {
        return rows
                .stream()
                .map(row -> SessionTaskResponse.from(
                        tasksById.get(row.taskId()),
                        row.taskCompleted()
                ))
                .toList();
    }

}
