package com.swimming.backend.session.domain;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Getter
public class Session {

    private final Long id;
    private final Long userId;
    private final SessionType type;
    private final List<Long> taskIds;
    private final int plannedDurationSec;
    private Integer actualDurationSec;
    private final Instant startedAt;
    private Instant endedAt;
    private SessionStatus status;

    private Session(
            Long id,
            Long userId,
            SessionType type,
            List<Long> taskIds,
            int plannedDurationSec,
            Integer actualDurationSec,
            Instant startedAt,
            Instant endedAt,
            SessionStatus status
    ) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.taskIds = List.copyOf(taskIds);
        this.plannedDurationSec = plannedDurationSec;
        this.actualDurationSec = actualDurationSec;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.status = status;
    }

    public static Session startPersonal(
            Long userId,
            List<Long> taskIds,
            int plannedDurationSec
    ) {
        return new Session(
                null,
                userId,
                SessionType.PERSONAL,
                taskIds,
                plannedDurationSec,
                null,
                null,
                null,
                SessionStatus.IN_PROGRESS
        );
    }

    public static Session restore(
            Long id,
            Long userId,
            SessionType type,
            List<Long> taskIds,
            int plannedDurationSec,
            Integer actualDurationSec,
            Instant startedAt,
            Instant endedAt,
            SessionStatus status
    ) {
        return new Session(
                id,
                userId,
                type,
                taskIds,
                plannedDurationSec,
                actualDurationSec,
                startedAt,
                endedAt,
                status
        );
    }

    public void end(Instant endTime) {
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
    }
}
