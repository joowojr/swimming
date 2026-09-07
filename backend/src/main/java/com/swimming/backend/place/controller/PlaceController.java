package com.swimming.backend.place.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.place.dto.CityResponse;
import com.swimming.backend.place.usecase.PlaceUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@SecurityRequirements
@Tag(name = "장소", description = "세션 생성 모달과 개인 세션 화면의 배경 장소. 인증 없이 연다.")
@RestController
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceUseCase placeUseCase;

    @GetMapping("/api/public/places")
    public ResponseEntity<List<CityResponse>> getPlaces(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        List<CityResponse> places =
                placeUseCase.getPlaces();

        CacheControl cacheControl = CacheControl
                .maxAge(Duration.ofMinutes(5))
                .sMaxAge(Duration.ofHours(1))
                .cachePublic();

        return ResponseEntity.ok()
                .cacheControl(cacheControl)
                .body(places);
    }
}
