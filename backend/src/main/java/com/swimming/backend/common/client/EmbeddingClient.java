package com.swimming.backend.common.client;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

/** 임베딩 AI API 호출만 담당한다. */
@Service
@RequiredArgsConstructor
public class EmbeddingClient {

    private final EmbeddingModel embeddingModel;

    public float[] embed(String input) {
        return embeddingModel.embed(input);
    }
}
