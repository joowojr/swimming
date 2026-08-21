package com.swimming.backend.session.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.plan.service.DailyPlanService;
import com.swimming.backend.session.domain.Session;
import com.swimming.backend.session.dto.web.ActiveSessionResponse;
import com.swimming.backend.session.dto.web.ActiveSessionTaskResponse;
import com.swimming.backend.session.dto.web.SessionResponse;
import com.swimming.backend.session.dto.web.StartPersonalSessionRequest;
import com.swimming.backend.session.service.SessionService;
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

        Session session = Session.startPersonal(
                userId,
                taskIds,
                request.plannedDurationSec()
        );
        return SessionResponse.from(sessionService.save(session));
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public Optional<ActiveSessionResponse> getActive(Long userId) {
        return sessionService.getActive(userId)
                .map(session -> toActiveResponse(userId, session));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public SessionResponse end(Long userId, Long sessionId) {
        Session session = sessionService.getOwned(userId, sessionId);
        session.end(clock.instant());
        return SessionResponse.from(sessionService.save(session));
    }

    private ActiveSessionResponse toActiveResponse(Long userId, Session session) {
        Map<Long, TaskReference> tasksById = taskService
                .getAllByIds(userId, session.getTaskIds())
                .stream()
                .collect(Collectors.toMap(TaskReference::id, Function.identity()));

        if (tasksById.size() != session.getTaskIds().size()) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }

        List<ActiveSessionTaskResponse> tasks = session.getTaskIds()
                .stream()
                .map(tasksById::get)
                .map(ActiveSessionTaskResponse::from)
                .toList();
        return ActiveSessionResponse.from(session, tasks);
    }
}
