package com.swimming.backend.common.config.llm;

import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * OpenAI 호환 API를 노출하는 로컬 서버용 옵션.
 *
 * <p>전송 규약은 OpenAI와 같으므로 {@link OpenAiChatOptions}를 그대로 쓰되,
 * reasoning 계열과 logprobs 같은 OpenAI 전용 파라미터는 보내지 않는다.
 * 호환 서버는 모르는 파라미터를 거부하는 경우가 있다.
 */
@Component
@ConditionalOnProperty(
        name = "app.llm.provider",
        havingValue = "openai-compatible"
)
public class OpenAiCompatibleChatOptionsFactory implements ChatOptionsFactory {

    private final LlmProperties properties;

    public OpenAiCompatibleChatOptionsFactory(LlmProperties properties) {
        this.properties = properties;
    }

    @Override
    public OpenAiChatOptions.Builder create() {
        return OpenAiChatOptions.builder()
                .model(properties.model())
                .maxTokens(properties.maxTokens())
                .temperature(properties.temperature());
    }
}
