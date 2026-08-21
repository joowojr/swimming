package com.swimming.backend.place.controller;

import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.place.dto.CityResponse;
import com.swimming.backend.place.service.PlaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cities")
@RequiredArgsConstructor
public class CityController {

    private final PlaceService placeService;

    @GetMapping
    public ResponseEntity<List<CityResponse>> getCities(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ResponseEntity.ok(placeService.getCities());
    }
}
