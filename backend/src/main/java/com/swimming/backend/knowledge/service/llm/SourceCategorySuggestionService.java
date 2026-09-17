package com.swimming.backend.knowledge.service.llm;

import com.swimming.backend.common.config.llm.ChatOptionsFactory;
import com.swimming.backend.common.logging.LlmUsageLogger;
import com.swimming.backend.common.prompt.PromptKey;
import com.swimming.backend.common.prompt.PromptRepository;
import com.swimming.backend.knowledge.dto.out.CategorySuggestionInput;
import com.swimming.backend.knowledge.dto.out.CategorySuggestionResult;
import com.swimming.backend.knowledge.prompt.CategorySuggestionInputSerializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Folder에 모인 문서를 함께 보고 Category 묶음 초안을 만든다.
 *
 * <p>여기서는 LLM 호출까지만 한다. 돌려받은 것이 쓸 수 있는 구성인지 판정하는 것은
 * {@link com.swimming.backend.knowledge.service.graph.CategorySuggestionValidator}가 맡는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SourceCategorySuggestionService {

    private static final String LOG_FEATURE = "category-suggestion";

    private final ChatClient chatClient;
    private final PromptRepository promptRepository;
    private final ChatOptionsFactory chatOptionsFactory;
    private final LlmUsageLogger usageLogger;

    public CategorySuggestionResult suggest(CategorySuggestionInput input) {
        String systemPrompt = promptRepository.get(PromptKey.CATEGORY_SUGGESTION);
        String userMessage = CategorySuggestionInputSerializer.serialize(input);

        var response = chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .options(chatOptionsFactory.create())
                .call()
                .responseEntity(
                        CategorySuggestionResult.class,
                        spec -> spec
                                .useProviderStructuredOutput()
                                .validateSchema()
                );

        usageLogger.log(
                LOG_FEATURE,
                response.getResponse(),
                systemPrompt,
                userMessage,
                inputScale(input)
        );

        return response.getEntity();
    }

    private String inputScale(CategorySuggestionInput input) {
        return "documents=%d".formatted(input.items().size());
    }
}
