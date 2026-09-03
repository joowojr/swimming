package com.swimming.backend.place.dto;

import com.swimming.backend.place.domain.Place;

public record PlaceResponse(
        Long id,
        String name,
        BackgroundAssetResponse backgroundAsset,
        String defaultMusicUrl
) {
    public static PlaceResponse from(Place place, String backgroundAssetUrl) {
        return new PlaceResponse(
                place.getId(),
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
