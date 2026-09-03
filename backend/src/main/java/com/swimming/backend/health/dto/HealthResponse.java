package com.swimming.backend.health.dto;

public record HealthResponse(String status, Detail detail) {

    public record Detail(String postgres) {
    }
}
