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
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false)
    private CityEntity city;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "background_asset_type", nullable = false)
    private BackgroundAssetType backgroundAssetType;

    @Column(name = "background_asset_key", nullable = false, length = 2048)
    private String backgroundAssetKey;

    @Column(name = "thumbnail_asset_key", length = 2048)
    private String thumbnailAssetKey;

    @Column(name = "default_music_url", length = 2048)
    private String defaultMusicUrl;

    @Builder
    private PlaceEntity(
            CityEntity city,
            String name,
            BackgroundAssetType backgroundAssetType,
            String backgroundAssetKey,
            String thumbnailAssetKey,
            String defaultMusicUrl
    ) {
        this.city = city;
        this.name = name;
        this.backgroundAssetType = backgroundAssetType;
        this.backgroundAssetKey = backgroundAssetKey;
        this.thumbnailAssetKey = thumbnailAssetKey;
        this.defaultMusicUrl = defaultMusicUrl;
    }

    public static PlaceEntity create(
            CityEntity city,
            String name,
            BackgroundAssetType backgroundAssetType,
            String backgroundAssetKey,
            String thumbnailAssetKey,
            String defaultMusicUrl
    ) {
        return PlaceEntity.builder()
                .city(city)
                .name(name)
                .backgroundAssetType(backgroundAssetType)
                .backgroundAssetKey(backgroundAssetKey)
                .thumbnailAssetKey(thumbnailAssetKey)
                .defaultMusicUrl(defaultMusicUrl)
                .build();
    }

    public Place toDomain() {
        return Place.restore(
                id,
                city.toDomain(),
                name,
                backgroundAssetType,
                backgroundAssetKey,
                thumbnailAssetKey,
                defaultMusicUrl
        );
    }
}
