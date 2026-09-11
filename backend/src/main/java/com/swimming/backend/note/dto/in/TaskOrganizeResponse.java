package com.swimming.backend.note.dto.in;

import java.util.List;
import java.time.LocalDate;

public record TaskOrganizeResponse(
        Long runId,
        List<TaskSuggestionResponse> suggestions,
        List<UnclassifiedResponse> unclassified
) {
    public record TaskSuggestionResponse(
            String itemId,
            String sourceText,
            Long folderId,
            String folderName,
            String title,
            LocalDate planDate
    ) {
    }

    public record UnclassifiedResponse(
            String itemId,
            String sourceText,
            String title,
            LocalDate planDate
    ) {
    }
}
