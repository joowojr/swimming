package com.swimming.backend.session.dto.web;

import com.swimming.backend.place.domain.Place;

public record SessionPlaceResponse(
        Long id,
        Long cityId,
        String cityName,
        String name,
        String defaultMusicUrl
) {
    public static SessionPlaceResponse from(Place place) {
        return new SessionPlaceResponse(
                place.getId(),
                place.getCity().getId(),
                place.getCity().getName(),
                place.getName(),
                place.getDefaultMusicUrl()
        );
    }
}
