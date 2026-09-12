package com.swimming.backend.knowledge.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * AI 소화에 넣을 본문의 분량 정책.
 *
 * <p>저장하는 본문과 LLM에 넣는 본문은 다르다. 저장은 나중에 다시 볼 수 있게 온전히 하고,
 * LLM 입력은 문서가 무엇을 다루는지 판단할 만큼만 넣는다.
 *
 * @param maxInputLength LLM에 넣을 최대 글자 수. 이보다 짧은 문서는 손대지 않는다.
 * @param sectionBudget  헤딩 하나가 가져갈 최소 분량. 헤딩 줄과 본문을 합쳐 이만큼까지 남긴다.
 *                       구간 수가 적어 예산이 남으면 이 값보다 늘려 상한을 채운다.
 */
@Validated
@ConfigurationProperties("app.knowledge.digest")
public record LinkDigestProperties(

        @NotNull @Min(1000) Integer maxInputLength,
        @NotNull @Min(100) Integer sectionBudget
) {
}
