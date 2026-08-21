package com.swimming.backend.place.domain;

import lombok.Getter;

@Getter
public class City {

    private final Long id;
    private final String name;
    private final String countryCode;
    private final String timezone;

    private City(Long id, String name, String countryCode, String timezone) {
        this.id = id;
        this.name = name;
        this.countryCode = countryCode;
        this.timezone = timezone;
    }

    public static City restore(Long id, String name, String countryCode, String timezone) {
        return new City(id, name, countryCode, timezone);
    }
}
