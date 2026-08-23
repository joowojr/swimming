package com.swimming.backend.session.dto.web;

import com.swimming.backend.place.dto.BackgroundAssetResponse;
import com.swimming.backend.place.dto.PlaceReference;

public record SessionDetailPlaceResponse(
        Long id,
        Long cityId,
        String cityName,
        String name,
        BackgroundAssetResponse backgroundAsset,
        String defaultMusicUrl
) {
    public static SessionDetailPlaceResponse from(PlaceReference place) {
        return new SessionDetailPlaceResponse(
                place.id(),
                place.cityId(),
                place.cityName(),
                place.name(),
                new BackgroundAssetResponse(
                        place.backgroundAssetType(),
                        place.backgroundAssetKey(),
                        place.backgroundAssetUrl()
                ),
                place.defaultMusicUrl()
        );
    }
}
