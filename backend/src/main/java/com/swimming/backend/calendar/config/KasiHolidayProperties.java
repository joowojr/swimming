package com.swimming.backend.calendar.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("app.calendar.holiday.kasi")
public record KasiHolidayProperties(
        @NotBlank @Pattern(regexp = "https://.+") String baseUrl,
        String serviceKey,
        @NotNull Duration connectTimeout,
        @NotNull Duration responseTimeout
) {
    public KasiHolidayProperties {
        if (baseUrl != null) {
            baseUrl = baseUrl.strip();
        }
        serviceKey = serviceKey == null ? "" : serviceKey.strip();
    }
}
