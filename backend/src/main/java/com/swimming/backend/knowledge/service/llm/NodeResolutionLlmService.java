package com.swimming.backend.knowledge.service.llm;

import com.swimming.backend.common.config.llm.ChatOptionsFactory;
import com.swimming.backend.common.logging.LlmUsageLogger;
import com.swimming.backend.common.prompt.PromptKey;
import com.swimming.backend.common.prompt.PromptRepository;
import com.swimming.backend.knowledge.dto.out.NodeResolutionInput;
import com.swimming.backend.knowledge.dto.out.NodeResolutionResult;
import com.swimming.backend.knowledge.prompt.NodeResolutionInputSerializer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class NodeResolutionLlmService {

    private static final String LOG_FEATURE = "node-resolution";

    private final ChatClient chatClient;
    private final PromptRepository promptRepository;
    private final ChatOptionsFactory chatOptionsFactory;
    private final LlmUsageLogger usageLogger;

    public NodeResolutionLlmService(
            ChatClient chatClient,
            PromptRepository promptRepository,
            ChatOptionsFactory chatOptionsFactory,
            LlmUsageLogger usageLogger
    ) {
        this.chatClient = chatClient;
        this.promptRepository = promptRepository;
        this.chatOptionsFactory = chatOptionsFactory;
        this.usageLogger = usageLogger;
    }

    public NodeResolutionResult resolve(NodeResolutionInput input) {
        String systemPrompt = promptRepository.get(PromptKey.NODE_RESOLUTION);
        String userMessage = NodeResolutionInputSerializer.serialize(input);

        var response = chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .options(chatOptionsFactory.create())
                .call()
                .responseEntity(
                        NodeResolutionResult.class,
                        spec -> spec.useProviderStructuredOutput().validateSchema()
                );

        usageLogger.log(
                LOG_FEATURE,
                response.getResponse(),
                systemPrompt,
                userMessage,
                "candidates=%d existing=%d".formatted(
                        input.candidates().size(), input.existingSubjects().size()
                )
        );

        return response.getEntity();
    }
}
