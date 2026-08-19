package com.swimming.backend.auth.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32) String secret,
        @NotNull @DurationMin(seconds = 1) Duration accessTokenValidity,
        @NotNull @DurationMin(seconds = 1) Duration refreshTokenValidity
) {
}
