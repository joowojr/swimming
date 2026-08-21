package com.swimming.backend.session.dto.web;

import com.swimming.backend.session.domain.Session;
import com.swimming.backend.session.domain.SessionStatus;
import com.swimming.backend.session.domain.SessionType;

import java.time.Instant;
import java.util.List;

public record ActiveSessionResponse(
        Long id,
        SessionType type,
        SessionStatus status,
        int plannedDurationSec,
        Instant startedAt,
        List<ActiveSessionTaskResponse> tasks
) {
    public static ActiveSessionResponse from(
            Session session,
            List<ActiveSessionTaskResponse> tasks
    ) {
        return new ActiveSessionResponse(
                session.getId(),
                session.getType(),
                session.getStatus(),
                session.getPlannedDurationSec(),
                session.getStartedAt(),
                List.copyOf(tasks)
        );
    }
}
