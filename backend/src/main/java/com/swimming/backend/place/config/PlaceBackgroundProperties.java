package com.swimming.backend.place.config;

import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("app.place.background")
public record PlaceBackgroundProperties(
        @NotNull @DurationMin(seconds = 1) Duration urlValidity
) {
}
