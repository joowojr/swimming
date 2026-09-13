package com.swimming.backend.knowledge.dto.out;

import java.util.List;

/** 후보별 임베딩 검색 결과를 함께 전달하는 Subject 판정 입력. */
public record NodeResolutionInputV2(
        String summary,
        List<Candidate> candidates,
        List<ContextSubject> contextSubjects
) {
    public NodeResolutionInputV2(String summary, List<Candidate> candidates) {
        this(summary, candidates, List.of());
    }

    public NodeResolutionInputV2 {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        contextSubjects = contextSubjects == null ? List.of() : List.copyOf(contextSubjects);
    }

    public record Candidate(int index, String value, List<Match> matches) {
        public Candidate {
            matches = matches == null ? List.of() : List.copyOf(matches);
        }
    }

    /** 전체 입력에서 유일한 1-based 재사용 번호. */
    public record Match(int index, String value) {
    }

    public record ContextSubject(int index, String value) {
    }
}
