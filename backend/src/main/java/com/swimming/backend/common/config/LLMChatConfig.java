package com.swimming.backend.common.config;

import com.swimming.backend.common.config.llm.LlmProperties;
import com.swimming.backend.common.config.llm.LlmProvider;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LLMChatConfig {

    @Bean
    SimpleLoggerAdvisor simpleLoggerAdvisor() {
        return SimpleLoggerAdvisor.builder().build();
    }

    @Bean
    ChatClient chatClient(
            ChatClient.Builder builder,
            SimpleLoggerAdvisor simpleLoggerAdvisor,
            ChatModel chatModel,
            LlmProperties llmProperties
    ) {
        validateProviderMatchesChatModel(llmProperties.provider(), chatModel);

        return builder
                .defaultAdvisors(simpleLoggerAdvisor)
                .build();
    }

    /**
     * {@code app.llm.provider}와 {@code spring.ai.model.chat}이 어긋나면
     * 첫 LLM 호출에서야 알아보기 어려운 에러가 난다. 기동 시점에 잡는다.
     */
    private void validateProviderMatchesChatModel(
            LlmProvider provider,
            ChatModel chatModel
    ) {
        Class<? extends ChatModel> expected = switch (provider) {
            case OPENAI, OPENAI_COMPATIBLE -> OpenAiChatModel.class;
            case OLLAMA -> OllamaChatModel.class;
        };

        if (!expected.isInstance(chatModel)) {
            throw new IllegalStateException(
                    "app.llm.provider=%s expects a %s but spring.ai.model.chat resolved %s. Align the two properties."
                            .formatted(
                                    provider,
                                    expected.getSimpleName(),
                                    chatModel.getClass().getSimpleName()
                            )
            );
        }
    }
}
