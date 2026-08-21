package com.swimming.backend.place.domain;

import lombok.Getter;

@Getter
public class Place {

    private final Long id;
    private final Long cityId;
    private final String name;
    private final BackgroundAssetType backgroundAssetType;
    private final String backgroundAssetUrl;
    private final String defaultMusicUrl;

    private Place(
            Long id,
            Long cityId,
            String name,
            BackgroundAssetType backgroundAssetType,
            String backgroundAssetUrl,
            String defaultMusicUrl
    ) {
        this.id = id;
        this.cityId = cityId;
        this.name = name;
        this.backgroundAssetType = backgroundAssetType;
        this.backgroundAssetUrl = backgroundAssetUrl;
        this.defaultMusicUrl = defaultMusicUrl;
    }

    public static Place restore(
            Long id,
            Long cityId,
            String name,
            BackgroundAssetType backgroundAssetType,
            String backgroundAssetUrl,
            String defaultMusicUrl
    ) {
        return new Place(
                id,
                cityId,
                name,
                backgroundAssetType,
                backgroundAssetUrl,
                defaultMusicUrl
        );
    }
}
