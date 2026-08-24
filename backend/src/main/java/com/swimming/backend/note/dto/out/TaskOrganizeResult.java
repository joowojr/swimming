package com.swimming.backend.note.dto.out;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record TaskOrganizeResult(
        @JsonPropertyDescription(
                "Task candidates that have clear evidence for exactly one supplied project. Never include unrelated or ambiguous items."
        )
        List<TaskSuggestion> suggestions,

        @JsonPropertyDescription(
                "Polished preview items that cannot be assigned confidently to exactly one supplied project or are not safely actionable."
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
                    "Id of an existing project supplied in the input."
            )
            Long projectId,

            @JsonPropertyDescription(
                    "Short actionable task title."
            )
            String title,

            @JsonPropertyDescription(
                    "Confidence in the project classification from 0.0 to 1.0."
            )
            Double confidence
            ) {
    }

    public record UnclassifiedItem(

            @JsonPropertyDescription(
                    "Original portion of the user's memo represented by this item. Preserve spelling and wording verbatim."
            )
            String sourceText,

            @JsonPropertyDescription(
                    "Short, searchable title that preserves the original meaning without inventing an action."
            )
            String title
    ) {
    }
}
