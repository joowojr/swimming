package com.swimming.backend.place.dto;

import com.swimming.backend.place.domain.Place;

public record PlaceResponse(
        Long id,
        String name,
        String defaultMusicUrl
) {
    public static PlaceResponse from(Place place) {
        return new PlaceResponse(
                place.getId(),
                place.getName(),
                place.getDefaultMusicUrl()
        );
    }
}
