package com.swimming.backend.knowledge.dto.out;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.swagger.v3.oas.annotations.media.ArraySchema;

import java.util.List;

/**
 * Source 하나에 대한 AI 소화 결과.
 *
 * <p>설명은 <b>필드가 무엇인지</b>만 적는다. 무엇을 Topic으로 볼지, 어떤 이름을 Subject로
 * 쓸지의 판단 기준은 프롬프트가 소유한다. 양쪽에 같은 기준을 적으면 한쪽만 고쳤을 때
 * 조용히 갈라진다.
 */
public record SourceDigestResult(

        @JsonPropertyDescription("Core content of the document, in Korean.")
        String summary,

        @JsonPropertyDescription(
                "Single coarse area this document belongs to, in Korean. One or two words."
        )
        String category,

        @JsonPropertyDescription(
                "Single application purpose the document itself explains or supports. "
                        + "Always present, never null or empty."
        )
        String topic,

        @JsonPropertyDescription("Concepts the document directly covers, in order of prominence.")
        // 상한은 스키마로 막는다. 설명문에 숫자를 또 적으면 한쪽만 고쳤을 때 갈라진다.
        @ArraySchema(maxItems = 4)
        List<String> subjects
) {
}
