package com.swimming.backend.place.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @DisplayName("도시별 공간을 정렬된 카탈로그 응답으로 반환한다")
    void getsCitiesWithPlaces() {
        CityEntity lisbon = city(1L, "Lisbon", "PT");
        CityEntity tokyo = city(2L, "Tokyo", "JP");
        PlaceEntity alfama = place(11L, 1L, "Alfama Cafe");
        PlaceEntity shibuya = place(21L, 2L, "Shibuya Rooftop");
        when(cityRepository.findAllByOrderByIdAsc()).thenReturn(List.of(lisbon, tokyo));
        when(placeRepository.findAllByOrderByCityIdAscIdAsc())
                .thenReturn(List.of(alfama, shibuya));

        var response = placeService.getCities();

        assertThat(response).extracting(city -> city.name())
                .containsExactly("Lisbon", "Tokyo");
        assertThat(response.getFirst().places()).singleElement()
                .satisfies(place -> {
                    assertThat(place.id()).isEqualTo(11L);
                    assertThat(place.backgroundAsset().type())
                            .isEqualTo(BackgroundAssetType.VIDEO);
                });
    }

    @Test
    @DisplayName("공간과 도시 정보를 다른 도메인용 DTO로 반환한다")
    void getsPlaceReference() {
        CityEntity city = city(1L, "Lisbon", "PT");
        PlaceEntity place = place(11L, 1L, "Alfama Cafe");
        when(placeRepository.findById(11L)).thenReturn(Optional.of(place));
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city));

        var reference = placeService.getReference(11L);

        assertThat(reference.cityName()).isEqualTo("Lisbon");
        assertThat(reference.name()).isEqualTo("Alfama Cafe");
        assertThat(reference.backgroundAssetUrl())
                .isEqualTo("https://cdn.example.com/alfama.webm");
    }

    @Test
    @DisplayName("존재하지 않는 공간은 찾을 수 없다")
    void rejectsMissingPlace() {
        when(placeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> placeService.getReference(99L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLACE_NOT_FOUND));
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
                "https://cdn.example.com/alfama.webm",
                "https://youtu.be/default"
        );
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
