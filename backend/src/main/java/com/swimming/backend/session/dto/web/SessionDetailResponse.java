package com.swimming.backend.session.dto.web;

import com.swimming.backend.place.domain.Place;
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
        SessionDetailPlaceResponse place,
        String musicUrl,
        List<SessionTaskResponse> tasks
) {
    public static SessionDetailResponse from(
            Session session,
            Place place,
            String backgroundAssetUrl,
            List<SessionTaskResponse> tasks
    ) {
        return new SessionDetailResponse(
                session.getId(),
                session.getType(),
                session.getStatus(),
                session.getPlannedDurationSec(),
                session.getActualDurationSec(),
                session.getStartedAt(),
                session.getEndedAt(),
                SessionDetailPlaceResponse.from(place, backgroundAssetUrl),
                session.getMusicUrl(),
                List.copyOf(tasks)
        );
    }
}
