package com.swimming.backend.common.config.llm;

import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * OpenAI 공식 API용 옵션.
 *
 * <p>reasoning 모델은 maxCompletionTokens를, 그 외 모델은 maxTokens를 쓴다.
 * 둘은 상호 배타적이라 동시에 설정하면 API가 에러를 반환한다.
 */
@Component
@ConditionalOnProperty(
        name = "app.llm.provider",
        havingValue = "openai",
        matchIfMissing = true
)
public class OpenAiChatOptionsFactory implements ChatOptionsFactory {

    private final LlmProperties properties;

    public OpenAiChatOptionsFactory(LlmProperties properties) {
        this.properties = properties;
    }

    @Override
    public OpenAiChatOptions.Builder create() {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(properties.model())
                .logprobs(false);

        String reasoningEffort = properties.openai().reasoningEffort();

        if (StringUtils.hasText(reasoningEffort)) {
            builder.reasoningEffort(reasoningEffort)
                    .maxCompletionTokens(properties.maxTokens());
        } else {
            builder.maxTokens(properties.maxTokens())
                    .temperature(properties.temperature());
        }

        return builder;
    }
}
