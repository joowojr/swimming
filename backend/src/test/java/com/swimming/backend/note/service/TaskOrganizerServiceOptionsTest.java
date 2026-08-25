package com.swimming.backend.note.service;

import com.swimming.backend.note.prompt.TaskOrganizerPromptProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TaskOrganizerServiceOptionsTest {

    @Test
    @DisplayName("GPT-5 Chat Completions 요청에 low reasoning effort를 사용한다")
    void usesLowReasoningEffortForGpt5ChatCompletions() {
        TaskOrganizerService service = new TaskOrganizerService(
                mock(ChatClient.class),
                mock(TaskOrganizerPromptProvider.class),
                "gpt-5.6-luna"
        );

        OpenAiChatOptions options = service.createOptionsBuilder().build();

        assertThat(options.getModel()).isEqualTo("gpt-5.6-luna");
        assertThat(options.getReasoningEffort()).isEqualTo("low");
        assertThat(options.getMaxCompletionTokens()).isEqualTo(2_000);
    }
}
