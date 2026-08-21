package com.swimming.backend.place.dto;

import com.swimming.backend.place.domain.BackgroundAssetType;
import com.swimming.backend.place.domain.City;
import com.swimming.backend.place.domain.Place;

public record PlaceReference(
        Long id,
        Long cityId,
        String cityName,
        String name,
        BackgroundAssetType backgroundAssetType,
        String backgroundAssetUrl,
        String defaultMusicUrl
) {
    public static PlaceReference from(Place place, City city) {
        return new PlaceReference(
                place.getId(),
                city.getId(),
                city.getName(),
                place.getName(),
                place.getBackgroundAssetType(),
                place.getBackgroundAssetUrl(),
                place.getDefaultMusicUrl()
        );
    }
}
