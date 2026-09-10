package com.swimming.backend.knowledge.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Subject 재사용 판정 설정. */
@Validated
@ConfigurationProperties("app.knowledge.resolution")
public record KnowledgeResolutionProperties(
        @NotNull @Min(1) @Max(20) Integer similarSourceLimit,
        @NotNull @Min(1) @Max(20) Integer subjectTopK
) {
}
