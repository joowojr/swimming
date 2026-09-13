package com.swimming.backend.knowledge.service.llm;

import java.util.List;
import java.util.UUID;

/** LLM 프로토콜의 C/R 번호를 노출하지 않는 Subject 판정 요청. */
public record NodeResolutionLlmRequest(
        String summary,
        List<Candidate> candidates,
        List<ReusableSubject> contextSubjects
) {

    public NodeResolutionLlmRequest {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        contextSubjects = contextSubjects == null ? List.of() : List.copyOf(contextSubjects);
    }

    public record Candidate(
            String key,
            String value,
            List<ReusableSubject> matches
    ) {

        public Candidate {
            matches = matches == null ? List.of() : List.copyOf(matches);
        }
    }

    public record ReusableSubject(UUID subjectId, String title) {
    }
}
