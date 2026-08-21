package com.swimming.backend.place.repository.entity;

import com.swimming.backend.common.entity.BaseTimeEntity;
import com.swimming.backend.place.domain.BackgroundAssetType;
import com.swimming.backend.place.domain.Place;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "places")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "city_id", nullable = false)
    private Long cityId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "background_asset_type", nullable = false)
    private BackgroundAssetType backgroundAssetType;

    @Column(name = "background_asset_url", nullable = false, length = 2048)
    private String backgroundAssetUrl;

    @Column(name = "default_music_url", length = 2048)
    private String defaultMusicUrl;

    private PlaceEntity(
            Long cityId,
            String name,
            BackgroundAssetType backgroundAssetType,
            String backgroundAssetUrl,
            String defaultMusicUrl
    ) {
        this.cityId = cityId;
        this.name = name;
        this.backgroundAssetType = backgroundAssetType;
        this.backgroundAssetUrl = backgroundAssetUrl;
        this.defaultMusicUrl = defaultMusicUrl;
    }

    public static PlaceEntity create(
            Long cityId,
            String name,
            BackgroundAssetType backgroundAssetType,
            String backgroundAssetUrl,
            String defaultMusicUrl
    ) {
        return new PlaceEntity(
                cityId,
                name,
                backgroundAssetType,
                backgroundAssetUrl,
                defaultMusicUrl
        );
    }

    public Place toDomain() {
        return Place.restore(
                id,
                cityId,
                name,
                backgroundAssetType,
                backgroundAssetUrl,
                defaultMusicUrl
        );
    }
}
