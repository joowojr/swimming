package com.swimming.backend.note.dto.out;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * 폴더가 이미 정해진 상태의 결과. 분류 판단이 없으므로 {@code folderId} 를 받지 않는다.
 *
 * <p>설명은 <b>필드가 무엇인지</b>만 적는다. 어느 바구니에 넣을지의 판단 기준은 프롬프트가
 * 소유한다. 양쪽에 같은 기준을 적으면 한쪽만 고쳤을 때 조용히 갈라진다.
 */
public record TaskExtractResult(

        @JsonPropertyDescription("Actionable items found in the memo, in memo order.")
        List<ExtractedTask> tasks,

        @JsonPropertyDescription("Meaningful memo content that is not an action, in memo order.")
        List<UnclassifiedItem> unclassified
) {

    public record ExtractedTask(

            @JsonPropertyDescription("Original portion of the user's memo that produced this item.")
            String sourceText,

            @JsonPropertyDescription("Short actionable task title.")
            String title
    ) {
    }

    public record UnclassifiedItem(

            @JsonPropertyDescription("Original portion of the user's memo represented by this item.")
            String sourceText,

            @JsonPropertyDescription("Short title that preserves the original meaning.")
            String title
    ) {
    }
}
