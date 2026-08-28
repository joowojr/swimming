package com.swimming.backend.session.domain;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
public class Session {

    private final Long id;
    private final Long userId;
    private final SessionType type;
    private final Long placeId;
    private List<SessionTask> tasks;
    private String musicUrl;
    private int plannedDurationSec;
    private Integer actualDurationSec;
    private final Instant startedAt;
    private Instant endedAt;
    private SessionStatus status;
    private String summary;

    private Session(
            Long id,
            Long userId,
            SessionType type,
            Long placeId,
            List<SessionTask> tasks,
            String musicUrl,
            int plannedDurationSec,
            Integer actualDurationSec,
            Instant startedAt,
            Instant endedAt,
            SessionStatus status,
            String summary
    ) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.placeId = placeId;
        this.tasks = List.copyOf(tasks);
        this.musicUrl = musicUrl;
        this.plannedDurationSec = plannedDurationSec;
        this.actualDurationSec = actualDurationSec;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.status = status;
        this.summary = summary;
    }

    public static Session startPersonal(
            Long userId,
            Long placeId,
            List<Long> taskIds,
            int plannedDurationSec
    ) {
        return new Session(
                null,
                userId,
                SessionType.PERSONAL,
                placeId,
                taskIds.stream().map(SessionTask::of).toList(),
                null,
                plannedDurationSec,
                null,
                null,
                null,
                SessionStatus.IN_PROGRESS,
                null
        );
    }

    public static Session restore(
            Long id,
            Long userId,
            SessionType type,
            Long placeId,
            List<SessionTask> tasks,
            String musicUrl,
            int plannedDurationSec,
            Integer actualDurationSec,
            Instant startedAt,
            Instant endedAt,
            SessionStatus status,
            String summary
    ) {
        return new Session(
                id,
                userId,
                type,
                placeId,
                tasks,
                musicUrl,
                plannedDurationSec,
                actualDurationSec,
                startedAt,
                endedAt,
                status,
                summary
        );
    }

    public List<Long> getTaskIds() {
        return tasks.stream().map(SessionTask::taskId).toList();
    }

    public void end(
            Instant endTime,
            String summary,
            Map<Long, Boolean> completionByTaskId
    ) {
        if (status != SessionStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.SESSION_ALREADY_ENDED);
        }

        Instant effectiveEndTime = endTime.isBefore(startedAt) ? startedAt : endTime;
        long elapsedSeconds = Duration.between(startedAt, effectiveEndTime).toSeconds();
        actualDurationSec = Math.toIntExact(elapsedSeconds);
        endedAt = effectiveEndTime;
        status = elapsedSeconds >= plannedDurationSec
                ? SessionStatus.COMPLETED
                : SessionStatus.INTERRUPTED;
        tasks = tasks.stream()
                .map(task -> completionByTaskId.containsKey(task.taskId())
                        ? task.recordCompletion(completionByTaskId.get(task.taskId()))
                        : task)
                .toList();
        this.summary = summary;
    }

    public void updateMusicUrl(String musicUrl) {
        if (status != SessionStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        this.musicUrl = musicUrl;
    }

    public void updatePlannedDuration(int plannedDurationSec) {
        if (status != SessionStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        if (plannedDurationSec < 60 || plannedDurationSec > 86400) {
            throw new BusinessException(ErrorCode.INVALID_SESSION_DURATION);
        }
        this.plannedDurationSec = plannedDurationSec;
    }
}
