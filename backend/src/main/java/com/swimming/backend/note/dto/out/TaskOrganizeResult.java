package com.swimming.backend.note.dto.out;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * 설명은 <b>필드가 무엇인지</b>만 적는다. 어느 바구니에 넣을지의 판단 기준은 프롬프트의
 * {@code # Result} 가 소유한다. 양쪽에 같은 기준을 적으면 한쪽만 고쳤을 때 조용히 갈라진다.
 */
public record TaskOrganizeResult(
        @JsonPropertyDescription(
                "Task candidates assigned to a supplied folder, in memo order."
        )
        List<TaskSuggestion> suggestions,

        @JsonPropertyDescription(
                "Memo content left without a folder, in memo order."
        )
        List<UnclassifiedItem> unclassified
) {

    public record TaskSuggestion(

            @JsonPropertyDescription(
                    "Action type. Always CREATE_TASK."
            )
            String type,

            @JsonPropertyDescription(
                    "Original portion of the user's memo that produced this action."
            )
            String sourceText,

            @JsonPropertyDescription(
                    "Id of an existing folder supplied in the input."
            )
            Long folderId,

            @JsonPropertyDescription(
                    "Short actionable task title."
            )
            String title
            ) {
    }

    public record UnclassifiedItem(

            @JsonPropertyDescription(
                    "Original portion of the user's memo represented by this item."
            )
            String sourceText,

            @JsonPropertyDescription(
                    "Short title that preserves the original meaning."
            )
            String title
    ) {
    }
}
