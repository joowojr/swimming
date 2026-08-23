package com.swimming.backend.place.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.place.config.PlaceBackgroundProperties;
import com.swimming.backend.place.domain.BackgroundAssetType;
import com.swimming.backend.place.repository.CityRepository;
import com.swimming.backend.place.repository.PlaceRepository;
import com.swimming.backend.place.repository.entity.CityEntity;
import com.swimming.backend.place.repository.entity.PlaceEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlaceVideoServiceTest {

    private static final String CDN_BASE_URL = "https://cdn.example.net";
    private static final String BACKGROUND_ASSET_KEY = "places/video/alfama.mp4";

    private CityRepository cityRepository;
    private PlaceRepository placeRepository;
    private PlaceVideoService placeVideoService;

    @BeforeEach
    void setUp() {
        cityRepository = mock(CityRepository.class);
        placeRepository = mock(PlaceRepository.class);
        placeVideoService = new PlaceVideoService(
                cityRepository,
                placeRepository,
                new PlaceBackgroundProperties(CDN_BASE_URL)
        );
    }

    @Test
    @DisplayName("다른 도메인용 DTO에는 오브젝트 키가 아니라 CDN 배경 URL을 담는다")
    void getsPlaceReferenceWithCdnBackgroundUrl() {
        givenPlace(BACKGROUND_ASSET_KEY);

        var reference = placeVideoService.getReference(11L);

        assertThat(reference.cityName()).isEqualTo("Lisbon");
        assertThat(reference.name()).isEqualTo("Alfama Cafe");
        assertThat(reference.backgroundAssetType()).isEqualTo(BackgroundAssetType.VIDEO);
        assertThat(reference.backgroundAssetKey()).isEqualTo(BACKGROUND_ASSET_KEY);
        assertThat(reference.backgroundAssetUrl())
                .isEqualTo("https://cdn.example.net/places/video/alfama.mp4");
    }

    @Test
    @DisplayName("배경 오브젝트 키가 비어 있으면 배경 URL을 비운다")
    void leavesBackgroundUrlEmptyWhenKeyIsBlank() {
        givenPlace("   ");

        var reference = placeVideoService.getReference(11L);

        assertThat(reference.backgroundAssetUrl()).isNull();
    }

    @Test
    @DisplayName("'/'로 시작하는 키도 슬래시가 겹치지 않는 CDN URL로 만든다")
    void normalizesLeadingSlashInKey() {
        givenPlace("/places/video/alfama.mp4");

        var reference = placeVideoService.getReference(11L);

        assertThat(reference.backgroundAssetUrl())
                .isEqualTo("https://cdn.example.net/places/video/alfama.mp4");
    }

    @Test
    @DisplayName("공백이나 한글이 섞인 키는 경로를 인코딩해 URL로 만든다")
    void encodesKeyPathSegments() {
        givenPlace("places/video/리스본 밤.mp4");

        var reference = placeVideoService.getReference(11L);

        assertThat(reference.backgroundAssetUrl())
                .isEqualTo("https://cdn.example.net/places/video/%EB%A6%AC%EC%8A%A4%EB%B3%B8%20%EB%B0%A4.mp4");
    }

    @Test
    @DisplayName("CDN 주소 끝에 '/'가 있어도 슬래시가 겹치지 않는다")
    void normalizesTrailingSlashInCdnBaseUrl() {
        placeVideoService = new PlaceVideoService(
                cityRepository,
                placeRepository,
                new PlaceBackgroundProperties("https://cdn.example.net/")
        );
        givenPlace(BACKGROUND_ASSET_KEY);

        var reference = placeVideoService.getReference(11L);

        assertThat(reference.backgroundAssetUrl())
                .isEqualTo("https://cdn.example.net/places/video/alfama.mp4");
    }

    @Test
    @DisplayName("존재하지 않는 공간은 찾을 수 없다")
    void rejectsMissingPlace() {
        when(placeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> placeVideoService.getReference(99L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLACE_NOT_FOUND));
    }

    @Test
    @DisplayName("공간이 참조하는 도시가 없으면 찾을 수 없다")
    void rejectsMissingCity() {
        when(placeRepository.findById(11L))
                .thenReturn(Optional.of(place(11L, 1L, "Alfama Cafe", BACKGROUND_ASSET_KEY)));
        when(cityRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> placeVideoService.getReference(11L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLACE_NOT_FOUND));
    }

    private void givenPlace(String backgroundAssetKey) {
        when(placeRepository.findById(11L))
                .thenReturn(Optional.of(place(11L, 1L, "Alfama Cafe", backgroundAssetKey)));
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city(1L, "Lisbon", "PT")));
    }

    private CityEntity city(Long id, String name, String countryCode) {
        CityEntity entity = CityEntity.create(name, countryCode, "UTC");
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private PlaceEntity place(Long id, Long cityId, String name, String backgroundAssetKey) {
        PlaceEntity entity = PlaceEntity.create(
                cityId,
                name,
                BackgroundAssetType.VIDEO,
                backgroundAssetKey,
                "https://youtu.be/default"
        );
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
