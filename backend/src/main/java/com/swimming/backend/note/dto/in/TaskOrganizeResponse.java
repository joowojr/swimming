package com.swimming.backend.note.dto.in;

import java.util.List;

public record TaskOrganizeResponse(
        List<TaskSuggestionResponse> suggestions,
        List<UnclassifiedResponse> unclassified
) {

    public record TaskSuggestionResponse(
            String sourceText,
            Long folderId,
            String projectName,
            String title
    ) {
    }

    public record UnclassifiedResponse(
            String sourceText,
            String title
    ) {
    }
}
