package com.swimming.backend.knowledge.dto.in;

import java.util.List;

/**
 * Source 하나에 걸린 개념과 목적.
 *
 * <p>목록·저장 응답({@link SourceResponse})과 상세 응답({@link SourceDetailResponse})이
 * 공통으로 갖는 부분이다. 어느 쪽을 만들든 읽어야 하는 것이 같으므로 따로 이름을 준다.
 *
 * @param topic    소화가 끝난 Source면 반드시 하나 있다
 * @param subjects 최대 4개
 */
public record SourceConcepts(NodeRef topic, List<NodeRef> subjects) {

    /** 아직 소화되지 않았거나 소화에 실패한 Source. */
    public static SourceConcepts empty() {
        return new SourceConcepts(null, List.of());
    }
}
