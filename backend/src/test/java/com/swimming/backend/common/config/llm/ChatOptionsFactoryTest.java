package com.swimming.backend.common.config.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;

import static org.assertj.core.api.Assertions.assertThat;

class ChatOptionsFactoryTest {

    private static LlmProperties properties(
            LlmProvider provider,
            String model,
            LlmProperties.OpenAi openai,
            LlmProperties.Ollama ollama
    ) {
        return new LlmProperties(
                provider,
                model,
                2_000,
                0.0,
                openai,
                ollama
        );
    }

    @Nested
    @DisplayName("OpenAI 공식 API")
    class OpenAi {

        @Test
        @DisplayName("reasoning effort가 설정되면 maxCompletionTokens를 쓰고 maxTokens는 비워둔다")
        void usesMaxCompletionTokensWhenReasoningEffortIsSet() {
            OpenAiChatOptions options = new OpenAiChatOptionsFactory(
                    properties(
                            LlmProvider.OPENAI,
                            "gpt-5.6-luna",
                            new LlmProperties.OpenAi("low"),
                            null
                    )
            ).create().build();

            assertThat(options.getModel()).isEqualTo("gpt-5.6-luna");
            assertThat(options.getReasoningEffort()).isEqualTo("low");
            assertThat(options.getMaxCompletionTokens()).isEqualTo(2_000);
            assertThat(options.getMaxTokens()).isNull();
        }

        @Test
        @DisplayName("reasoning effort가 비어 있으면 일반 모델로 보고 maxTokens와 temperature를 쓴다")
        void usesMaxTokensWhenReasoningEffortIsAbsent() {
            OpenAiChatOptions options = new OpenAiChatOptionsFactory(
                    properties(
                            LlmProvider.OPENAI,
                            "gpt-4o-mini",
                            new LlmProperties.OpenAi(null),
                            null
                    )
            ).create().build();

            assertThat(options.getReasoningEffort()).isNull();
            assertThat(options.getMaxCompletionTokens()).isNull();
            assertThat(options.getMaxTokens()).isEqualTo(2_000);
            assertThat(options.getTemperature()).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("OpenAI 호환 로컬 서버")
    class OpenAiCompatible {

        @Test
        @DisplayName("로컬 서버가 거부할 수 있는 reasoning·logprobs 파라미터를 보내지 않는다")
        void omitsOpenAiOnlyParameters() {
            OpenAiChatOptions options = new OpenAiCompatibleChatOptionsFactory(
                    properties(
                            LlmProvider.OPENAI_COMPATIBLE,
                            "qwen3-8b",
                            new LlmProperties.OpenAi("low"),
                            null
                    )
            ).create().build();

            assertThat(options.getModel()).isEqualTo("qwen3-8b");
            assertThat(options.getMaxTokens()).isEqualTo(2_000);
            assertThat(options.getTemperature()).isEqualTo(0.0);
            assertThat(options.getReasoningEffort()).isNull();
            assertThat(options.getLogprobs()).isNull();
        }
    }

    @Nested
    @DisplayName("Ollama 네이티브 API")
    class Ollama {

        @Test
        @DisplayName("maxTokens를 numPredict로 매핑하고 numCtx와 keepAlive를 함께 보낸다")
        void mapsMaxTokensToNumPredict() {
            OllamaChatOptions options = new OllamaChatOptionsFactory(
                    properties(
                            LlmProvider.OLLAMA,
                            "qwen3:8b",
                            null,
                            new LlmProperties.Ollama(16_384, "10m", false)
                    )
            ).create().build();

            assertThat(options.getModel()).isEqualTo("qwen3:8b");
            assertThat(options.getNumPredict()).isEqualTo(2_000);
            assertThat(options.getNumCtx()).isEqualTo(16_384);
            assertThat(options.getKeepAlive()).isEqualTo("10m");
            assertThat(options.getTemperature()).isEqualTo(0.0);
            assertThat(options.getThinkOption().toJsonValue()).isEqualTo(false);
        }

        @Test
        @DisplayName("think를 켜면 thinking을 사용하도록 옵션을 넘긴다")
        void enablesThinkingWhenRequested() {
            OllamaChatOptions options = new OllamaChatOptionsFactory(
                    properties(
                            LlmProvider.OLLAMA,
                            "qwen3:8b",
                            null,
                            new LlmProperties.Ollama(null, null, true)
                    )
            ).create().build();

            assertThat(options.getThinkOption().toJsonValue()).isEqualTo(true);
        }

        @Test
        @DisplayName("ollama 하위 설정이 비어 있어도 기본 옵션만으로 만들어진다")
        void buildsWithoutOllamaSpecificSettings() {
            OllamaChatOptions options = new OllamaChatOptionsFactory(
                    properties(LlmProvider.OLLAMA, "qwen3:8b", null, null)
            ).create().build();

            assertThat(options.getNumCtx()).isNull();
            assertThat(options.getKeepAlive()).isNull();
            assertThat(options.getNumPredict()).isEqualTo(2_000);
            assertThat(options.getThinkOption().toJsonValue()).isEqualTo(false);
        }
    }
}
