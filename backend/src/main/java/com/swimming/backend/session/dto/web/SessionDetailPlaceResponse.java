package com.swimming.backend.session.dto.web;

import com.swimming.backend.place.dto.BackgroundAssetResponse;
import com.swimming.backend.session.dto.projection.SessionWithPlaceRow;

public record SessionDetailPlaceResponse(
        Long id,
        Long cityId,
        String cityName,
        String name,
        BackgroundAssetResponse backgroundAsset,
        String defaultMusicUrl
) {
    public static SessionDetailPlaceResponse from(
            SessionWithPlaceRow row,
            String backgroundAssetUrl
    ) {
        return new SessionDetailPlaceResponse(
                row.placeId(),
                row.cityId(),
                row.cityName(),
                row.placeName(),
                new BackgroundAssetResponse(
                        row.backgroundAssetType(),
                        row.backgroundAssetKey(),
                        backgroundAssetUrl
                ),
                row.defaultMusicUrl()
        );
    }
}
