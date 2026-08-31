package com.swimming.backend.place.repository.entity;

import com.swimming.backend.common.entity.BaseTimeEntity;
import com.swimming.backend.place.domain.BackgroundAssetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceEntityTest {

    @Test
    @DisplayName("공간 엔티티는 시간 공통 필드를 상속하고 도메인으로 변환된다")
    void convertsToDomain() {
        CityEntity city = CityEntity.create("Lisbon", "PT", "Europe/Lisbon");
        ReflectionTestUtils.setField(city, "id", 1L);
        PlaceEntity entity = PlaceEntity.create(
                city,
                "Alfama Cafe",
                BackgroundAssetType.VIDEO,
                "https://cdn.example.com/alfama.webm",
                "https://youtu.be/default"
        );
        ReflectionTestUtils.setField(entity, "id", 11L);

        var place = entity.toDomain();

        assertThat(entity).isInstanceOf(BaseTimeEntity.class);
        assertThat(place.getId()).isEqualTo(11L);
        assertThat(place.getCityId()).isEqualTo(1L);
        assertThat(place.getCity().getName()).isEqualTo("Lisbon");
        assertThat(place.getBackgroundAssetType()).isEqualTo(BackgroundAssetType.VIDEO);
    }
}
