package com.swimming.backend.session.dto.web;

import com.swimming.backend.session.domain.SessionStatus;
import com.swimming.backend.session.domain.SessionType;
import com.swimming.backend.session.dto.projection.SessionWithPlaceRow;

import java.time.Instant;
import java.util.List;

public record SessionDetailResponse(
        Long id,
        SessionType type,
        SessionStatus status,
        int plannedDurationSec,
        int focusDurationSec,
        int breakDurationSec,
        int repeatCount,
        Integer actualDurationSec,
        Instant startedAt,
        Instant endedAt,
        SessionDetailPlaceResponse place,
        String musicUrl,
        List<SessionTaskResponse> tasks
) {
    public static SessionDetailResponse from(
            SessionWithPlaceRow row,
            String backgroundAssetUrl,
            String thumbnailAssetUrl,
            List<SessionTaskResponse> tasks
    ) {
        return new SessionDetailResponse(
                row.sessionId(),
                row.type(),
                row.status(),
                row.plannedDurationSec(),
                row.focusDurationSec(),
                row.breakDurationSec(),
                row.repeatCount(),
                row.actualDurationSec(),
                row.startedAt(),
                row.endedAt(),
                SessionDetailPlaceResponse.from(row, backgroundAssetUrl, thumbnailAssetUrl),
                row.musicUrl(),
                List.copyOf(tasks)
        );
    }
}
