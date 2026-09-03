package com.swimming.backend.common.config.llm;

/**
 *
 * <p>어떤 ChatModel 오토컨피그가 활성화되는지는 Spring AI의
 * {@code spring.ai.model.chat} 프로퍼티가 결정한다. 이 enum은 그 위에서
 * 요청 옵션을 어떤 형태로 만들지를 결정한다.
 */
public enum LlmProvider {

    /** OpenAI 공식 API. reasoning 모델 전용 옵션을 사용할 수 있다. */
    OPENAI,

    /**
     * OpenAI 호환 API를 노출하는 로컬 서버 (LM Studio, vLLM, llama.cpp, Ollama의 /v1 등).
     * 전송 규약은 OpenAI와 같지만 reasoning 계열 옵션은 지원하지 않는다.
     */
    OPENAI_COMPATIBLE,

    /** Ollama 네이티브 API. */
    OLLAMA
}
