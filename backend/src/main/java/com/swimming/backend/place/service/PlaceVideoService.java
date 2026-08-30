package com.swimming.backend.place.service;

import com.swimming.backend.place.config.PlaceBackgroundProperties;
import com.swimming.backend.place.domain.Place;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

/**
 * 배경 에셋의 저장 방식(비공개 S3 + 오브젝트 키)을 감추고,
 * place 도메인 밖으로 나가는 값은 항상 재생 가능한 CDN URL이 되도록 변환한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceVideoService {

    private final PlaceBackgroundProperties placeBackgroundProperties;

    public String resolveBackgroundUrl(Place place) {
        String backgroundAssetKey = place.getBackgroundAssetKey();
        if (backgroundAssetKey == null || backgroundAssetKey.isBlank()) {
            log.warn("[SWIMMING_PLACE] 배경 에셋 키가 비어 있어 배경 URL을 만들지 않습니다");
            return null;
        }

        String objectKey = backgroundAssetKey.strip().replaceAll("^/+", "");
        String url = placeBackgroundProperties.cdnBaseUrl()
                + "/"
                + UriUtils.encodePath(objectKey, StandardCharsets.UTF_8);

        log.debug("[SWIMMING_PLACE] 배경 URL 생성 key={} url={}", objectKey, url);
        return url;
    }
}
