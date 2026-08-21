package com.swimming.backend.session.dto.web;

import com.swimming.backend.place.dto.BackgroundAssetResponse;
import com.swimming.backend.place.dto.PlaceReference;

public record SessionPlaceResponse(
        Long id,
        Long cityId,
        String cityName,
        String name,
        BackgroundAssetResponse backgroundAsset,
        String defaultMusicUrl
) {
    public static SessionPlaceResponse from(PlaceReference place) {
        return new SessionPlaceResponse(
                place.id(),
                place.cityId(),
                place.cityName(),
                place.name(),
                new BackgroundAssetResponse(
                        place.backgroundAssetType(),
                        place.backgroundAssetUrl()
                ),
                place.defaultMusicUrl()
        );
    }
}
