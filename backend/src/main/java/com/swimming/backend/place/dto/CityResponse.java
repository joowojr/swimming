package com.swimming.backend.place.dto;

import com.swimming.backend.place.domain.City;

import java.util.List;

public record CityResponse(
        Long id,
        String name,
        String countryCode,
        List<PlaceResponse> places
) {
    public static CityResponse from(City city, List<PlaceResponse> places) {
        return new CityResponse(
                city.getId(),
                city.getName(),
                city.getCountryCode(),
                List.copyOf(places)
        );
    }
}
