package com.swimming.backend.health.controller;

import com.swimming.backend.health.dto.HealthResponse;
import com.swimming.backend.health.service.HealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;

    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        healthService.verifyDatabaseConnection();
        return ResponseEntity.ok(new HealthResponse(
                "UP",
                new HealthResponse.Detail("UP")
        ));
    }
}
