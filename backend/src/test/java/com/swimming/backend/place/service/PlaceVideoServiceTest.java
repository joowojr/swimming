package com.swimming.backend.place.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.common.s3.S3Service;
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

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceVideoServiceTest {

    private static final Duration URL_VALIDITY = Duration.ofHours(6);
    private static final String BACKGROUND_ASSET_KEY = "places/video/alfama.mp4";
    private static final String PRESIGNED_URL =
            "https://bucket.s3.amazonaws.com/alfama.mp4?X-Amz-Signature=abc";

    private CityRepository cityRepository;
    private PlaceRepository placeRepository;
    private S3Service s3Service;
    private PlaceVideoService placeVideoService;

    @BeforeEach
    void setUp() {
        cityRepository = mock(CityRepository.class);
        placeRepository = mock(PlaceRepository.class);
        s3Service = mock(S3Service.class);
        placeVideoService = new PlaceVideoService(
                cityRepository,
                placeRepository,
                s3Service,
                new PlaceBackgroundProperties(URL_VALIDITY)
        );
    }

    @Test
    @DisplayName("다른 도메인용 DTO에는 오브젝트 키가 아니라 설정된 유효기간의 presigned URL을 담는다")
    void getsPlaceReferenceWithPresignedBackgroundUrl() {
        when(placeRepository.findById(11L))
                .thenReturn(Optional.of(place(11L, 1L, "Alfama Cafe", BACKGROUND_ASSET_KEY)));
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city(1L, "Lisbon", "PT")));
        when(s3Service.presignGetUrl(BACKGROUND_ASSET_KEY, URL_VALIDITY))
                .thenReturn(PRESIGNED_URL);

        var reference = placeVideoService.getReference(11L);

        assertThat(reference.cityName()).isEqualTo("Lisbon");
        assertThat(reference.name()).isEqualTo("Alfama Cafe");
        assertThat(reference.backgroundAssetType()).isEqualTo(BackgroundAssetType.VIDEO);
        assertThat(reference.backgroundAssetKey()).isEqualTo(BACKGROUND_ASSET_KEY);
        assertThat(reference.backgroundAssetUrl()).isEqualTo(PRESIGNED_URL);
        verify(s3Service).presignGetUrl(BACKGROUND_ASSET_KEY, URL_VALIDITY);
    }

    @Test
    @DisplayName("배경 오브젝트 키가 비어 있으면 서명하지 않고 URL을 비운다")
    void leavesBackgroundUrlEmptyWhenKeyIsBlank() {
        when(placeRepository.findById(11L))
                .thenReturn(Optional.of(place(11L, 1L, "Alfama Cafe", "   ")));
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city(1L, "Lisbon", "PT")));

        var reference = placeVideoService.getReference(11L);

        assertThat(reference.backgroundAssetUrl()).isNull();
        verify(s3Service, never()).presignGetUrl(any(), any());
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
