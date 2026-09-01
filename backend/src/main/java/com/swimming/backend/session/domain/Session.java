package com.swimming.backend.session.domain;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;

@Getter
public class Session {

    private final Long id;
    private final Long userId;
    private final SessionType type;
    private final Long placeId;
    private List<SessionTask> tasks;
    private String musicUrl;
    private int plannedDurationSec;
    private int focusDurationSec;
    private int breakDurationSec;
    private int repeatCount;
    private Integer actualDurationSec;
    private final Instant startedAt;
    private Instant endedAt;
    private SessionStatus status;
    private String summary;

    @Builder
    private Session(
            Long id,
            Long userId,
            SessionType type,
            Long placeId,
            List<SessionTask> tasks,
            String musicUrl,
            int plannedDurationSec,
            int focusDurationSec,
            int breakDurationSec,
            int repeatCount,
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
        this.focusDurationSec = focusDurationSec;
        this.breakDurationSec = breakDurationSec;
        this.repeatCount = repeatCount;
        this.actualDurationSec = actualDurationSec;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.status = status;
        this.summary = summary;
    }

    public static Session createPersonal(
            Long userId,
            Long placeId,
            List<Long> taskIds,
            int plannedDurationSec,
            int focusDurationSec,
            int breakDurationSec,
            int repeatCount
    ) {
        long calculatedPlannedDurationSec = (long) focusDurationSec * repeatCount
                + (long) breakDurationSec * Math.max(0, repeatCount - 1);
        if (focusDurationSec < 60
                || breakDurationSec < 0
                || breakDurationSec > 3600
                || repeatCount < 1
                || repeatCount > 8
                || calculatedPlannedDurationSec > 86400
                || plannedDurationSec != calculatedPlannedDurationSec) {
            throw new BusinessException(ErrorCode.INVALID_SESSION_DURATION);
        }
        return Session.builder()
                .userId(userId)
                .type(SessionType.PERSONAL)
                .placeId(placeId)
                .tasks(taskIds.stream().map(SessionTask::of).toList())
                .plannedDurationSec(plannedDurationSec)
                .focusDurationSec(focusDurationSec)
                .breakDurationSec(breakDurationSec)
                .repeatCount(repeatCount)
                .status(SessionStatus.IN_PROGRESS)
                .build();
    }

    public static Session createPersonal(Long userId, Long placeId, List<Long> taskIds, int plannedDurationSec) {
        return createPersonal(userId, placeId, taskIds, plannedDurationSec, plannedDurationSec, 0, 1);
    }

    public static Session restore(
            Long id,
            Long userId,
            SessionType type,
            Long placeId,
            List<SessionTask> tasks,
            String musicUrl,
            int plannedDurationSec,
            int focusDurationSec,
            int breakDurationSec,
            int repeatCount,
            Integer actualDurationSec,
            Instant startedAt,
            Instant endedAt,
            SessionStatus status,
            String summary
    ) {
        return Session.builder()
                .id(id)
                .userId(userId)
                .type(type)
                .placeId(placeId)
                .tasks(tasks)
                .musicUrl(musicUrl)
                .plannedDurationSec(plannedDurationSec)
                .focusDurationSec(focusDurationSec)
                .breakDurationSec(breakDurationSec)
                .repeatCount(repeatCount)
                .actualDurationSec(actualDurationSec)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .status(status)
                .summary(summary)
                .build();
    }

    public static Session restore(Long id, Long userId, SessionType type, Long placeId,
                                  List<SessionTask> tasks, String musicUrl, int plannedDurationSec,
                                  Integer actualDurationSec, Instant startedAt, Instant endedAt,
                                  SessionStatus status, String summary) {
        return restore(id, userId, type, placeId, tasks, musicUrl, plannedDurationSec,
                plannedDurationSec, 0, 1, actualDurationSec, startedAt, endedAt, status, summary);
    }

    public List<Long> getTaskIds() {
        return tasks.stream().map(SessionTask::taskId).toList();
    }

    public void end(
            Instant endTime,
            String summary,
            List<Long> completedTaskIds
    ) {
        if (status != SessionStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.SESSION_ALREADY_ENDED);
        }

        var completedTaskIdSet = new HashSet<>(completedTaskIds);
        if (completedTaskIdSet.size() != completedTaskIds.size()
                || !new HashSet<>(getTaskIds()).containsAll(completedTaskIdSet)) {
            throw new BusinessException(ErrorCode.INVALID_SESSION_TASKS);
        }

        Instant effectiveEndTime = endTime.isBefore(startedAt) ? startedAt : endTime;
        long elapsedSeconds = Duration.between(startedAt, effectiveEndTime).toSeconds();
        actualDurationSec = Math.toIntExact(elapsedSeconds);
        endedAt = effectiveEndTime;
        status = elapsedSeconds >= plannedDurationSec
                ? SessionStatus.COMPLETED
                : SessionStatus.INTERRUPTED;
        tasks = tasks.stream()
                .map(task -> completedTaskIdSet.contains(task.taskId())
                        ? task.recordCompletion(true)
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

    public void updateFocusDuration(int focusDurationSec) {
        if (status != SessionStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }

        long calculatedPlannedDurationSec = calculatePlannedDuration(focusDurationSec);
        if (focusDurationSec < 60 || calculatedPlannedDurationSec > 86400) {
            throw new BusinessException(ErrorCode.INVALID_SESSION_DURATION);
        }

        this.focusDurationSec = focusDurationSec;
        this.plannedDurationSec = Math.toIntExact(calculatedPlannedDurationSec);
    }

    private long calculatePlannedDuration(int focusDurationSec) {
        return (long) focusDurationSec * repeatCount
                + (long) breakDurationSec * Math.max(0, repeatCount - 1);
    }

}
