package com.swimming.backend.auth.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.auth.google")
public record GoogleAuthProperties(
        @NotBlank String clientId
) {
}
