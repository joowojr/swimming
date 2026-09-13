package com.swimming.backend.knowledge.service.llm;

import java.util.UUID;

/** LLM의 임시 인덱스를 실제 후보 key와 Subject ID로 변환한 판정. */
public record NodeResolutionLlmDecision(
        String candidateKey,
        UUID reusedSubjectId,
        String newSubjectTitle
) {

    public static NodeResolutionLlmDecision reuse(String candidateKey, UUID subjectId) {
        return new NodeResolutionLlmDecision(candidateKey, subjectId, null);
    }

    public static NodeResolutionLlmDecision create(String candidateKey, String title) {
        return new NodeResolutionLlmDecision(candidateKey, null, title);
    }

    public boolean reusesSubject() {
        return reusedSubjectId != null;
    }
}
