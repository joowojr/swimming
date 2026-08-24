package com.swimming.backend.note.service;

import com.swimming.backend.note.dto.out.TaskOrganizeResult;
import com.swimming.backend.note.dto.out.TaskOrganizerInput;
import com.swimming.backend.note.prompt.TaskOrganizerInputSerializer;
import com.swimming.backend.note.prompt.TaskOrganizerPromptProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

@Service
public class TaskOrganizerService {

    private final ChatClient chatClient;
    private final TaskOrganizerPromptProvider promptProvider;
    private final String model;
    private static final String DEFAULT_MODEL = "gpt-5.6-luna";

    @Autowired
    public TaskOrganizerService(
            ChatClient chatClient,
            TaskOrganizerPromptProvider promptProvider
    ) {
        this(chatClient, promptProvider, DEFAULT_MODEL);
    }

    TaskOrganizerService(
            ChatClient chatClient,
            TaskOrganizerPromptProvider promptProvider,
            String model
    ) {
        this.chatClient = chatClient;
        this.promptProvider = promptProvider;
        this.model = model;
    }

    public TaskOrganizeResult organize(TaskOrganizerInput input) {
        return chatClient.prompt()
                .system(promptProvider.get())
                .user(TaskOrganizerInputSerializer.serialize(input))
                .options(createOptionsBuilder())
                .call()
                .entity(
                        TaskOrganizeResult.class,
                        spec -> spec
                                .useProviderStructuredOutput()
                                .validateSchema()
                );
    }

    OpenAiChatOptions.Builder createOptionsBuilder() {
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(model)
                .logprobs(false);

        if (model.startsWith("gpt-5")) {
            optionsBuilder
                    .reasoningEffort("low")
                    .maxCompletionTokens(2_000);
        } else {
            optionsBuilder.maxTokens(2_000)
                    .temperature(0.0);
        }

        return optionsBuilder;
    }

//    private String serialize(TaskOrganizerInput input) {
//        try {
//            return objectMapper.writeValueAsString(input);
//        } catch (JsonProcessingException e) {
//            throw new IllegalArgumentException(
//                    "Failed to serialize task organizer input.",
//                    e
//            );
//        }
//    }
}
