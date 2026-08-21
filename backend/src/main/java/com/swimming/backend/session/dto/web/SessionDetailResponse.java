package com.swimming.backend.session.dto.web;

import com.swimming.backend.place.dto.PlaceReference;
import com.swimming.backend.session.domain.Session;
import com.swimming.backend.session.domain.SessionStatus;
import com.swimming.backend.session.domain.SessionType;

import java.time.Instant;
import java.util.List;

public record SessionDetailResponse(
        Long id,
        SessionType type,
        SessionStatus status,
        int plannedDurationSec,
        Integer actualDurationSec,
        Instant startedAt,
        Instant endedAt,
        SessionPlaceResponse place,
        String musicUrl,
        List<ActiveSessionTaskResponse> tasks
) {
    public static SessionDetailResponse from(
            Session session,
            PlaceReference place,
            List<ActiveSessionTaskResponse> tasks
    ) {
        return new SessionDetailResponse(
                session.getId(),
                session.getType(),
                session.getStatus(),
                session.getPlannedDurationSec(),
                session.getActualDurationSec(),
                session.getStartedAt(),
                session.getEndedAt(),
                SessionPlaceResponse.from(place),
                session.getMusicUrl(),
                List.copyOf(tasks)
        );
    }
}
