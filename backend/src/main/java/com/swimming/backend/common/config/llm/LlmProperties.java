package com.swimming.backend.common.config.llm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 채팅 모델 호출 옵션. provider에 따라 어떤 하위 설정이 쓰이는지가 달라진다.
 *
 * <p>구조화 출력은 설정 대상이 아니다. 세 provider의 옵션 타입이 모두
 * {@code StructuredOutputChatOptions}를 구현하므로 JSON Schema는 항상 API 파라미터로 전달된다.
 *
 * @param provider    옵션을 만들 방식. {@code spring.ai.model.chat}과 짝을 맞춰야 한다.
 * @param model       모델 이름
 * @param maxTokens   응답 최대 토큰
 * @param temperature 샘플링 온도
 * @param openai      {@link LlmProvider#OPENAI}에서만 쓰이는 설정
 * @param ollama      {@link LlmProvider#OLLAMA}에서만 쓰이는 설정
 */
@Validated
@ConfigurationProperties("app.llm")
public record LlmProperties(

        @NotNull LlmProvider provider,
        @NotBlank String model,
        @NotNull @Positive Integer maxTokens,
        @NotNull Double temperature,
        OpenAi openai,
        Ollama ollama
) {

    public LlmProperties {
        if (openai == null) {
            openai = new OpenAi(null);
        }
        if (ollama == null) {
            ollama = new Ollama(null, null, false);
        }
    }

    /**
     * @param reasoningEffort reasoning 모델의 추론 강도(low/medium/high).
     *                        값이 있으면 reasoning 모델로 간주해 maxCompletionTokens를 쓰고,
     *                        비어 있으면 일반 모델로 보고 maxTokens와 temperature를 쓴다.
     */
    public record OpenAi(String reasoningEffort) {
    }

    /**
     * @param numCtx    컨텍스트 윈도우 크기. Ollama 기본값(2048)은 시스템 프롬프트와
     *                  Folder/Task 컨텍스트를 담기에 부족해 조용히 잘린다.
     * @param keepAlive 모델을 메모리에 유지할 시간 (예: {@code 5m})
     * @param think     추론 모델의 thinking 사용 여부. thinking은 max-tokens 예산을 함께 쓰고
     *                  응답도 느려지므로 분류 작업에서는 끄는 편이 낫다.
     */
    public record Ollama(Integer numCtx, String keepAlive, boolean think) {
    }
}
