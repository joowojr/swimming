package com.swimming.backend.knowledge.dto.in;

import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.SourceProcessingStatus;
import com.swimming.backend.knowledge.dto.out.SourceDigestResult;

import java.util.UUID;

/**
 * @param result 성공했을 때만 채운다.
 */
public record SourceDigestResponse(
        UUID sourceId,
        String title,
        SourceProcessingStatus status,
        SourceDigestResult result
) {

    public static SourceDigestResponse of(KnowledgeSource source, SourceDigestResult result) {
        return new SourceDigestResponse(
                source.getId(),
                source.getNode().getTitle(),
                source.getProcessingStatus(),
                result
        );
    }
}
