package com.swimming.backend.place.usecase;

import com.swimming.backend.place.domain.BackgroundAssetType;
import com.swimming.backend.place.domain.City;
import com.swimming.backend.place.domain.Place;
import com.swimming.backend.place.dto.CityResponse;
import com.swimming.backend.place.service.PlaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlaceUseCaseTest {

    private PlaceService placeService;
    private PlaceUseCase placeUseCase;

    @BeforeEach
    void setUp() {
        placeService = mock(PlaceService.class);
        placeUseCase = new PlaceUseCase(placeService);
    }

    @Test
    @DisplayName("도시별 공간을 정렬된 카탈로그로 반환한다")
    void getsCitiesWithPlaces() {
        when(placeService.getCities()).thenReturn(List.of(
                city(1L, "Lisbon", "PT"),
                city(2L, "Tokyo", "JP")
        ));
        when(placeService.getPlaces()).thenReturn(List.of(
                place(11L, 1L, "Alfama Cafe", "places/lisbon/alfama.mp4"),
                place(21L, 2L, "Shibuya Rooftop", "places/tokyo/shibuya.mp4")
        ));

        List<CityResponse> response = placeUseCase.getCities();

        assertThat(response).extracting(CityResponse::name)
                .containsExactly("Lisbon", "Tokyo");
        assertThat(response.getFirst().places()).singleElement()
                .satisfies(place -> {
                    assertThat(place.id()).isEqualTo(11L);
                    assertThat(place.name()).isEqualTo("Alfama Cafe");
                    assertThat(place.defaultMusicUrl()).isEqualTo("https://youtu.be/default");
                });
    }

    @Test
    @DisplayName("공간이 없는 도시는 빈 목록으로 반환한다")
    void returnsEmptyPlacesForCityWithoutPlace() {
        when(placeService.getCities()).thenReturn(List.of(city(1L, "Lisbon", "PT")));
        when(placeService.getPlaces()).thenReturn(List.of());

        List<CityResponse> response = placeUseCase.getCities();

        assertThat(response).singleElement()
                .satisfies(city -> assertThat(city.places()).isEmpty());
    }

    private City city(Long id, String name, String countryCode) {
        return City.restore(id, name, countryCode, "UTC");
    }

    private Place place(Long id, Long cityId, String name, String backgroundAssetKey) {
        return Place.restore(
                id,
                cityId,
                name,
                BackgroundAssetType.VIDEO,
                backgroundAssetKey,
                "https://youtu.be/default"
        );
    }
}
