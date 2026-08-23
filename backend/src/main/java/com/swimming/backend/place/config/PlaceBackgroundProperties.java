package com.swimming.backend.place.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.place.background")
public record PlaceBackgroundProperties(
        @NotBlank @Pattern(regexp = "https?://.+") String cdnBaseUrl
) {
    public PlaceBackgroundProperties {
        if (cdnBaseUrl != null) {
            cdnBaseUrl = cdnBaseUrl.strip().replaceAll("/+$", "");
        }
    }
}
