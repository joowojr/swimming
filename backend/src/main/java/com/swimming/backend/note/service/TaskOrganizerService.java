package com.swimming.backend.note.service;

import com.swimming.backend.common.config.llm.ChatOptionsFactory;
import com.swimming.backend.common.logging.LlmUsageLogger;
import com.swimming.backend.note.dto.out.TaskExtractResult;
import com.swimming.backend.note.dto.out.TaskOrganizeResult;
import com.swimming.backend.note.dto.out.TaskOrganizerInput;
import com.swimming.backend.note.prompt.TaskOrganizerInputSerializer;
import com.swimming.backend.common.prompt.PromptKey;
import com.swimming.backend.common.prompt.PromptRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class TaskOrganizerService {

    private static final String LOG_FEATURE = "task-organizer";
    private static final String LOG_FEATURE_EXTRACT = "task-extractor";

    private final ChatClient chatClient;
    private final PromptRepository promptRepository;
    private final ChatOptionsFactory chatOptionsFactory;
    private final LlmUsageLogger usageLogger;

    public TaskOrganizerService(
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

    /**
     * 구조화 출력은 provider와 무관하게 항상 켠다.
     *
     * <p>{@code useProviderStructuredOutput}은 JSON Schema를 프롬프트가 아니라 API 파라미터로
     * 넘겨 형식을 강제한다. {@code validateSchema}는 그렇게 받은 응답을 다시 검증하고
     * 실패하면 오류를 붙여 재요청한다. 형식은 앞쪽이, 내용은 뒤쪽이 책임진다.
     *
     * <p>{@code responseEntity}로 받는 이유는 변환된 결과와 함께 응답 메타데이터가 필요해서다.
     * 토큰 사용량은 거기에만 들어 있다.
     */
    public TaskOrganizeResult organize(TaskOrganizerInput input) {
        String systemPrompt = promptRepository.get(PromptKey.TASK_ORGANIZER);
        String userMessage = TaskOrganizerInputSerializer.serialize(input);

        var response = chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .options(chatOptionsFactory.create())
                .call()
                .responseEntity(
                        TaskOrganizeResult.class,
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

    /**
     * 폴더가 이미 정해진 경우. 분류 단계가 없으므로 폴더 후보 판단을 요구하지 않는다.
     */
    public TaskExtractResult extract(TaskOrganizerInput input) {
        String systemPrompt = promptRepository.get(PromptKey.TASK_EXTRACTOR);
        String userMessage = TaskOrganizerInputSerializer.serialize(input);

        var response = chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .options(chatOptionsFactory.create())
                .call()
                .responseEntity(
                        TaskExtractResult.class,
                        spec -> spec
                                .useProviderStructuredOutput()
                                .validateSchema()
                );

        usageLogger.log(
                LOG_FEATURE_EXTRACT,
                response.getResponse(),
                systemPrompt,
                userMessage,
                inputScale(input)
        );

        return response.getEntity();
    }

    private String inputScale(TaskOrganizerInput input) {
        return "folders=%d tasks=%d memo=%d".formatted(
                input.folders().size(),
                input.tasks().size(),
                input.memo() == null ? 0 : input.memo().length()
        );
    }
}
