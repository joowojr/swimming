package com.swimming.backend.place.service;

import com.swimming.backend.place.config.PlaceBackgroundProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceVideoServiceTest {

    private static final String CDN_BASE_URL = "https://cdn.example.net";
    private static final String BACKGROUND_ASSET_KEY = "places/video/alfama.mp4";

    private PlaceVideoService placeVideoService;

    @BeforeEach
    void setUp() {
        placeVideoService = new PlaceVideoService(new PlaceBackgroundProperties(CDN_BASE_URL));
    }

    @Test
    @DisplayName("공간의 오브젝트 키를 CDN 배경 URL로 변환한다")
    void resolvesCdnBackgroundUrl() {
        String url = placeVideoService.resolveBackgroundUrl(BACKGROUND_ASSET_KEY);

        assertThat(url).isEqualTo("https://cdn.example.net/places/video/alfama.mp4");
    }

    @Test
    @DisplayName("배경 오브젝트 키가 비어 있으면 배경 URL을 비운다")
    void leavesBackgroundUrlEmptyWhenKeyIsBlank() {
        assertThat(placeVideoService.resolveBackgroundUrl("   ")).isNull();
    }

    @Test
    @DisplayName("썸네일 오브젝트 키를 CDN URL로 변환한다")
    void resolvesCdnThumbnailUrl() {
        String url = placeVideoService.resolveThumbnailUrl("places/thumbnails/alfama.mp4");

        assertThat(url).isEqualTo("https://cdn.example.net/places/thumbnails/alfama.mp4");
    }

    @Test
    @DisplayName("썸네일 키가 없으면 썸네일 URL을 비운다")
    void leavesThumbnailUrlEmptyWhenKeyIsMissing() {
        assertThat(placeVideoService.resolveThumbnailUrl(null)).isNull();
    }

    @Test
    @DisplayName("'/'로 시작하는 키도 슬래시가 겹치지 않는 CDN URL로 만든다")
    void normalizesLeadingSlashInKey() {
        String url = placeVideoService.resolveBackgroundUrl("/places/video/alfama.mp4");

        assertThat(url).isEqualTo("https://cdn.example.net/places/video/alfama.mp4");
    }

    @Test
    @DisplayName("공백이나 한글이 섞인 키는 경로를 인코딩해 URL로 만든다")
    void encodesKeyPathSegments() {
        String url = placeVideoService.resolveBackgroundUrl("places/video/리스본 밤.mp4");

        assertThat(url)
                .isEqualTo("https://cdn.example.net/places/video/%EB%A6%AC%EC%8A%A4%EB%B3%B8%20%EB%B0%A4.mp4");
    }

    @Test
    @DisplayName("CDN 주소 끝에 '/'가 있어도 슬래시가 겹치지 않는다")
    void normalizesTrailingSlashInCdnBaseUrl() {
        placeVideoService = new PlaceVideoService(
                new PlaceBackgroundProperties("https://cdn.example.net/")
        );

        String url = placeVideoService.resolveBackgroundUrl(BACKGROUND_ASSET_KEY);

        assertThat(url).isEqualTo("https://cdn.example.net/places/video/alfama.mp4");
    }

}
