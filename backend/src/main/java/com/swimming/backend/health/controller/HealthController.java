package com.swimming.backend.health.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import com.swimming.backend.health.dto.HealthResponse;
import com.swimming.backend.health.service.HealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SecurityRequirements
@Tag(name = "헬스체크", description = "헬스 페이지(`/health`)와 배포 확인용. 인증 없이 연다.")
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
