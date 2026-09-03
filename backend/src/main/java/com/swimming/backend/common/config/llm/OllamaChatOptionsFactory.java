package com.swimming.backend.common.config.llm;

import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Ollama 네이티브 API용 옵션.
 *
 * <p>Ollama의 응답 길이 제한은 num_predict이므로 maxTokens를 그쪽으로 매핑한다.
 * 빌더 메서드가 상위 타입을 반환하는 구조라 체이닝 대신 문장 단위로 설정한다.
 */
@Component
@ConditionalOnProperty(
        name = "app.llm.provider",
        havingValue = "ollama"
)
public class OllamaChatOptionsFactory implements ChatOptionsFactory {

    private final LlmProperties properties;

    public OllamaChatOptionsFactory(LlmProperties properties) {
        this.properties = properties;
    }

    @Override
    public OllamaChatOptions.Builder create() {
        OllamaChatOptions.Builder builder = OllamaChatOptions.builder();

        builder.model(properties.model());
        builder.temperature(properties.temperature());
        builder.numPredict(properties.maxTokens());

        LlmProperties.Ollama ollama = properties.ollama();

        if (ollama.numCtx() != null) {
            builder.numCtx(ollama.numCtx());
        }
        if (StringUtils.hasText(ollama.keepAlive())) {
            builder.keepAlive(ollama.keepAlive());
        }

        if (ollama.think()) {
            builder.enableThinking();
        } else {
            builder.disableThinking();
        }

        return builder;
    }
}
