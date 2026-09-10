package com.swimming.backend.note.dto.out;

public record LlmCallSnapshot(
        String stage,
        String provider,
        String model,
        String promptHash,
        long latencyMs,
        Integer inputTokens,
        Long cachedInputTokens,
        Long cacheWriteTokens,
        Integer outputTokens
) {
}
