package com.swimming.backend.common.config.llm;

import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * provider별 채팅 요청 옵션을 만든다.
 *
 * <p>{@code ChatClient.ChatClientRequestSpec#options}가 빌드된 옵션이 아니라 빌더를 받으므로
 * 빌더를 그대로 반환한다.
 *
 * <p>구현체는 {@code app.llm.provider} 값에 따라 하나만 빈으로 등록된다.
 */
public interface ChatOptionsFactory {

    ChatOptions.Builder<?> create();
}
