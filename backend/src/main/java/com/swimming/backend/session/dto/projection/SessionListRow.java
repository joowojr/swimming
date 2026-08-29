package com.swimming.backend.session.dto.projection;

import com.swimming.backend.place.domain.BackgroundAssetType;
import com.swimming.backend.session.domain.SessionStatus;
import com.swimming.backend.session.domain.SessionType;

import java.time.Instant;

public record SessionListRow(
        Long sessionId,
        Long userId,
        SessionType type,
        Long placeId,
        Long taskId,
        Boolean taskCompleted,
        String musicUrl,
        int plannedDurationSec,
        Integer actualDurationSec,
        Instant startedAt,
        Instant endedAt,
        SessionStatus status,
        String summary,
        Long cityId,
        String cityName,
        String placeName,
        BackgroundAssetType backgroundAssetType,
        String backgroundAssetKey,
        String defaultMusicUrl
) {
}
