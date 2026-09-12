package com.swimming.backend.knowledge.dto.out;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** 미해결 Subject 후보마다 기존 노드를 재사용할지 새 값을 만들지 정한 결과. */
public record NodeResolutionResult(
        List<Decision> decisions
) {
    public enum Action {
        REUSE,
        CREATE
    }

    public record Decision(
            @JsonPropertyDescription("1-based index in subjects-to-resolve this decision answers.")
            int candidateIndex,

            @JsonPropertyDescription("REUSE an existing subject or CREATE a new subject.")
            Action action,

            @JsonPropertyDescription(
                    "1-based index in reusable-subjects for REUSE; 0 for CREATE."
            )
            int reuseIndex,

            @JsonPropertyDescription(
                    "New canonical subject value for CREATE; empty string for REUSE."
            )
            String value
    ) {
    }
}
