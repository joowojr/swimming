package com.swimming.backend.knowledge.service;

import com.swimming.backend.common.config.llm.ChatOptionsFactory;
import com.swimming.backend.common.logging.LlmUsageLogger;
import com.swimming.backend.common.prompt.PromptKey;
import com.swimming.backend.common.prompt.PromptRepository;
import com.swimming.backend.knowledge.dto.out.SourceDigestInput;
import com.swimming.backend.knowledge.dto.out.SourceDigestResult;
import com.swimming.backend.knowledge.prompt.SourceDigestInputSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Source 하나를 소화해 Summary·Topic·Subject를 만든다.
 *
 * <p>여기서는 LLM 호출까지만 한다. Subject / Topic resolution과 관계 저장은
 * 전역 그래프를 봐야 하므로 이후 단계가 맡는다.
 */
@Slf4j
@Service
public class SourceDigestService {

    private static final String LOG_FEATURE = "source-digest";

    private final ChatClient chatClient;
    private final PromptRepository promptRepository;
    private final ChatOptionsFactory chatOptionsFactory;
    private final LlmUsageLogger usageLogger;
    private final DigestContentTrimmer contentTrimmer;

    public SourceDigestService(
            ChatClient chatClient,
            PromptRepository promptRepository,
            ChatOptionsFactory chatOptionsFactory,
            LlmUsageLogger usageLogger,
            DigestContentTrimmer contentTrimmer
    ) {
        this.chatClient = chatClient;
        this.promptRepository = promptRepository;
        this.chatOptionsFactory = chatOptionsFactory;
        this.usageLogger = usageLogger;
        this.contentTrimmer = contentTrimmer;
    }

    /**
     * 분량 조정은 여기서 한다. 호출하는 쪽이 빠뜨릴 수 없게 하기 위해서다.
     */
    public SourceDigestResult digest(SourceDigestInput input) {
        DigestContentTrimmer.Result trimmed = contentTrimmer.trim(input.content());

        if (trimmed.trimmed()) {
            log.info(
                    "[{}] trimmed content {} -> {} chars url={}",
                    LOG_FEATURE, input.content().length(), trimmed.content().length(), input.url()
            );
        }

        SourceDigestInput trimmedInput = new SourceDigestInput(
                input.title(), input.url(), trimmed.content(), input.existingTopics()
        );

        String systemPrompt = promptRepository.get(PromptKey.SOURCE_DIGEST);
        String userMessage = SourceDigestInputSerializer.serialize(trimmedInput);

        var response = chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .options(chatOptionsFactory.create())
                .call()
                .responseEntity(
                        SourceDigestResult.class,
                        spec -> spec
                                .useProviderStructuredOutput()
                                .validateSchema()
                );

        usageLogger.log(
                LOG_FEATURE,
                response.getResponse(),
                systemPrompt,
                userMessage,
                inputScale(trimmedInput)
        );

        return response.getEntity();
    }

    private String inputScale(SourceDigestInput input) {
        return "title=%d content=%d".formatted(
                input.title() == null ? 0 : input.title().length(),
                input.content() == null ? 0 : input.content().length()
        );
    }
}
