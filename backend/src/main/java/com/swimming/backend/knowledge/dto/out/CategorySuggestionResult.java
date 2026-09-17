package com.swimming.backend.knowledge.dto.out;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * 모델이 내놓은 Category 초안.
 *
 * <p>설명은 <b>필드가 무엇인지</b>만 적는다. 무엇을 한 묶음으로 볼지의 판단 기준은
 * 프롬프트가 소유한다. 양쪽에 같은 기준을 적으면 한쪽만 고쳤을 때 조용히 갈라진다.
 */
public record CategorySuggestionResult(

        @JsonPropertyDescription(
                "Groups of documents that belong together. "
                        + "Empty when the documents do not split into meaningful groups."
        )
        List<Category> categories
) {

    public record Category(

            @JsonPropertyDescription(
                    "Name of this group in Korean. Two to five words."
            )
            String title,

            @JsonPropertyDescription(
                    "1-based index numbers of the documents in this group, "
                            + "taken from the index attribute in the input."
            )
            List<Integer> documentIndexes
    ) {
    }
}
