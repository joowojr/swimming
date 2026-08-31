package com.swimming.backend.session.dto.web;

import com.swimming.backend.place.dto.BackgroundAssetResponse;
import com.swimming.backend.place.domain.Place;

public record SessionDetailPlaceResponse(
        Long id,
        Long cityId,
        String cityName,
        String name,
        BackgroundAssetResponse backgroundAsset,
        String defaultMusicUrl
) {
    public static SessionDetailPlaceResponse from(Place place, String backgroundAssetUrl) {
        return new SessionDetailPlaceResponse(
                place.getId(),
                place.getCity().getId(),
                place.getCity().getName(),
                place.getName(),
                new BackgroundAssetResponse(
                        place.getBackgroundAssetType(),
                        place.getBackgroundAssetKey(),
                        backgroundAssetUrl
                ),
                place.getDefaultMusicUrl()
        );
    }
}
