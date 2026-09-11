package com.swimming.backend.place.domain;

import lombok.Getter;
import lombok.Builder;

@Getter
public class Place {

    private final Long id;
    private final City city;
    private final String name;
    private final BackgroundAssetType backgroundAssetType;
    private final String backgroundAssetKey;
    private final String thumbnailAssetKey;
    private final String defaultMusicUrl;

    @Builder
    private Place(
            Long id,
            City city,
            String name,
            BackgroundAssetType backgroundAssetType,
            String backgroundAssetKey,
            String thumbnailAssetKey,
            String defaultMusicUrl
    ) {
        this.id = id;
        this.city = city;
        this.name = name;
        this.backgroundAssetType = backgroundAssetType;
        this.backgroundAssetKey = backgroundAssetKey;
        this.thumbnailAssetKey = thumbnailAssetKey;
        this.defaultMusicUrl = defaultMusicUrl;
    }

    public static Place restore(
            Long id,
            City city,
            String name,
            BackgroundAssetType backgroundAssetType,
            String backgroundAssetKey,
            String thumbnailAssetKey,
            String defaultMusicUrl
    ) {
        return Place.builder()
                .id(id)
                .city(city)
                .name(name)
                .backgroundAssetType(backgroundAssetType)
                .backgroundAssetKey(backgroundAssetKey)
                .thumbnailAssetKey(thumbnailAssetKey)
                .defaultMusicUrl(defaultMusicUrl)
                .build();
    }

    public Long getCityId() {
        return city.getId();
    }
}
