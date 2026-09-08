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
            @JsonPropertyDescription("Candidate value from the current source, copied exactly.")
            String candidate,

            @JsonPropertyDescription("REUSE an existing subject or CREATE a new subject.")
            Action action,

            @JsonPropertyDescription(
                    "1-based existing subject index for REUSE; 0 for CREATE."
            )
            int subjectIndex,

            @JsonPropertyDescription(
                    "New canonical subject value for CREATE; empty string for REUSE."
            )
            String value
    ) {
    }
}
