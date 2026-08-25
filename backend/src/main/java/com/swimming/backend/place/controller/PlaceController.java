package com.swimming.backend.place.controller;

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
