package com.swimming.backend.knowledge.dto.out;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** 후보별 매칭 번호로 기존 Subject 재사용 여부를 정하는 결과. */
public record NodeResolutionResultV2(List<Decision> decisions) {
    public record Decision(
            @JsonPropertyDescription("1-based candidate index this decision answers.")
            int candidateIndex,
            @JsonPropertyDescription("Globally unique 1-based R index for reuse; 0 for create.")
            int reuseIndex,
            @JsonPropertyDescription("Canonical new Subject name for create; empty for reuse.")
            String value
    ) {
    }
}
