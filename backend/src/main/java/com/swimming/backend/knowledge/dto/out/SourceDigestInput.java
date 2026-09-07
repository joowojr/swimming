package com.swimming.backend.knowledge.dto.out;

import java.util.List;

/**
 * 소화 대상 문서 하나. Source 단위로만 분석하므로 Folder의 다른 Source는 넣지 않는다.
 *
 * @param existingTopics 같은 사용자가 이미 만들어 둔 Topic 이름. 재사용을 강제하지 않고
 *                       이름이 불필요하게 갈라지지 않도록 참고 맥락으로만 넘긴다.
 */
public record SourceDigestInput(
        String title,
        String url,
        String content,
        List<String> existingTopics
) {

    public SourceDigestInput {
        existingTopics = existingTopics == null ? List.of() : List.copyOf(existingTopics);
    }
}
