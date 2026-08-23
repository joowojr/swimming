package com.swimming.backend.session.dto.web;

import com.swimming.backend.place.dto.PlaceReference;

public record SessionPlaceResponse(
        Long id,
        Long cityId,
        String cityName,
        String name,
        String defaultMusicUrl
) {
    public static SessionPlaceResponse from(PlaceReference place) {
        return new SessionPlaceResponse(
                place.id(),
                place.cityId(),
                place.cityName(),
                place.name(),
                place.defaultMusicUrl()
        );
    }
}
