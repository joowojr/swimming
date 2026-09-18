package com.swimming.backend.task.dto.in;

import com.swimming.backend.knowledge.domain.KnowledgeSource;

import java.util.UUID;

/** 할 일에 붙어 있는 링크 하나. */
public record TaskSourceResponse(
        UUID sourceId,
        Long folderId,
        String title
) {
    public static TaskSourceResponse from(KnowledgeSource source) {
        // 제목의 주인은 SOURCE 노드다. 문서 행의 title은 노드에서 복사한 사본이다.
        return new TaskSourceResponse(source.getId(), source.getFolderId(), source.getNode().getTitle());
    }
}
