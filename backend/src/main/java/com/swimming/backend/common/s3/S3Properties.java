package com.swimming.backend.common.s3;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("cloud.aws.s3")
public record S3Properties(
        @NotBlank String bucket
) {
}
