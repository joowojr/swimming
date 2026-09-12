package com.swimming.backend.knowledge.dto.out;

import java.util.List;

/** 규칙 기반으로 해결되지 않은 Subject를 의미 기반으로 판정할 입력. */
public record NodeResolutionInput(
        String summary,
        List<Candidate> candidates,
        List<ExistingSubject> existingSubjects
) {
    public NodeResolutionInput {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        existingSubjects = existingSubjects == null ? List.of() : List.copyOf(existingSubjects);
    }

    /** LLM이 후보 값을 그대로 옮겨 적지 않고 가리킬 수 있도록 부여한 1-based index. */
    public record Candidate(int index, String value) {
    }

    /** LLM이 짧고 안정적인 값으로 재사용 대상을 선택할 수 있도록 부여한 1-based index. */
    public record ExistingSubject(int index, String value) {
    }
}
