package com.swimming.backend.place.service;

import com.swimming.backend.place.domain.BackgroundAssetType;
import com.swimming.backend.place.repository.CityRepository;
import com.swimming.backend.place.repository.PlaceRepository;
import com.swimming.backend.place.repository.entity.CityEntity;
import com.swimming.backend.place.repository.entity.PlaceEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlaceServiceTest {

    private CityRepository cityRepository;
    private PlaceRepository placeRepository;
    private PlaceService placeService;

    @BeforeEach
    void setUp() {
        cityRepository = mock(CityRepository.class);
        placeRepository = mock(PlaceRepository.class);
        placeService = new PlaceService(cityRepository, placeRepository);
    }

    @Test
    @DisplayName("도시를 id 순서로 반환한다")
    void getsCities() {
        when(cityRepository.findAllByOrderByIdAsc()).thenReturn(List.of(
                city(1L, "Lisbon", "PT"),
                city(2L, "Tokyo", "JP")
        ));

        assertThat(placeService.getCitiesAndPlaces()).extracting(city -> city.getName())
                .containsExactly("Lisbon", "Tokyo");
    }

    @Test
    @DisplayName("공간을 도시·id 순서로 반환한다")
    void getsPlaces() {
        when(placeRepository.findAllByOrderByCityIdAscIdAsc()).thenReturn(List.of(
                place(11L, 1L, "Alfama Cafe"),
                place(21L, 2L, "Shibuya Rooftop")
        ));

        assertThat(placeService.getPlaces()).extracting(place -> place.getName())
                .containsExactly("Alfama Cafe", "Shibuya Rooftop");
    }

    private CityEntity city(Long id, String name, String countryCode) {
        CityEntity entity = CityEntity.create(name, countryCode, "UTC");
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private PlaceEntity place(Long id, Long cityId, String name) {
        PlaceEntity entity = PlaceEntity.create(
                cityId,
                name,
                BackgroundAssetType.VIDEO,
                "places/video/alfama.mp4",
                "https://youtu.be/default"
        );
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
