package com.swimming.backend.knowledge.service.llm;

import com.swimming.backend.common.client.EmbeddingClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmbeddingServiceTest {

    @Test
    @DisplayName("입력 문자열을 EmbeddingModel에 그대로 전달하고 결과를 반환한다")
    void delegatesEmbeddingApiCall() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        float[] expected = {0.1f, 0.2f};
        when(embeddingModel.embed("요약")).thenReturn(expected);

        float[] actual = new EmbeddingClient(embeddingModel).embed("요약");

        assertThat(actual).isSameAs(expected);
        verify(embeddingModel).embed("요약");
    }
}
