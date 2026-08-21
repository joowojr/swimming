package com.swimming.backend.session.dto.web;

import com.swimming.backend.session.domain.Session;
import com.swimming.backend.session.domain.SessionStatus;
import com.swimming.backend.session.domain.SessionType;

import java.time.Instant;
import java.util.List;

public record SessionResponse(
        Long id,
        SessionType type,
        List<Long> taskIds,
        int plannedDurationSec,
        Integer actualDurationSec,
        Instant startedAt,
        Instant endedAt,
        SessionStatus status
) {
    public static SessionResponse from(Session session) {
        return new SessionResponse(
                session.getId(),
                session.getType(),
                List.copyOf(session.getTaskIds()),
                session.getPlannedDurationSec(),
                session.getActualDurationSec(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getStatus()
        );
    }
}
