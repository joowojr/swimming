package com.swimming.backend.common.logging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

/**
 * LLM 호출 한 번의 입력 크기와 토큰 사용량을 한 줄로 남긴다.
 *
 * <p>토큰 수는 provider가 알려준 값이라 정확하지만 프롬프트와 데이터를 나눠 주지 않는다.
 * 그래서 글자 수를 함께 찍어 고정 프롬프트와 사용자 데이터의 비율을 본다. 입력이 어디서
 * 커지는지 찾는 것이 목적이다.
 *
 * <p>사용량을 주지 않는 provider도 있어 없는 값은 -1로 남긴다.
 */
@Component
@Slf4j
public class LlmUsageLogger {

    private static final int UNKNOWN = -1;

    /**
     * @param feature   로그를 구분할 기능 이름
     * @param context   기능마다 다른 입력 규모. 예: {@code "folders=5 tasks=48"}
     */
    public void log(
            String feature,
            ChatResponse response,
            String systemPrompt,
            String userMessage,
            String context
    ) {
        if (!log.isInfoEnabled()) {
            return;
        }

        Usage usage = usageOf(response);

        log.info(
                "[{}] tokens prompt={} completion={} total={} | chars system={} user={} | {}",
                feature,
                tokenCount(usage == null ? null : usage.getPromptTokens()),
                tokenCount(usage == null ? null : usage.getCompletionTokens()),
                tokenCount(usage == null ? null : usage.getTotalTokens()),
                length(systemPrompt),
                length(userMessage),
                context
        );
    }

    private Usage usageOf(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        return response.getMetadata().getUsage();
    }

    private int tokenCount(Integer value) {
        return value == null ? UNKNOWN : value;
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }
}
