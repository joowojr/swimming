package com.swimming.backend.session.dto.web;

import com.swimming.backend.session.domain.Session;
import com.swimming.backend.session.domain.SessionStatus;
import com.swimming.backend.session.domain.SessionType;
import com.swimming.backend.place.dto.PlaceReference;

import java.time.Instant;
import java.util.List;

public record SessionResponse(
        Long id,
        SessionType type,
        List<Long> taskIds,
        SessionPlaceResponse place,
        String musicUrl,
        int plannedDurationSec,
        Integer actualDurationSec,
        Instant startedAt,
        Instant endedAt,
        SessionStatus status
) {
    public static SessionResponse from(Session session, PlaceReference place) {
        return new SessionResponse(
                session.getId(),
                session.getType(),
                List.copyOf(session.getTaskIds()),
                SessionPlaceResponse.from(place),
                session.getMusicUrl(),
                session.getPlannedDurationSec(),
                session.getActualDurationSec(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getStatus()
        );
    }
}
