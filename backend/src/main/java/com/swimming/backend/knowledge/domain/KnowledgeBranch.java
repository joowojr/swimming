package com.swimming.backend.knowledge.domain;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * 사용자가 만든 소속 / 구조. 의미 관계는 담지 않는다.
 * (parentNodeId, childNodeId)가 자연키다.
 */
@Getter
public class KnowledgeBranch {

    private final UUID parentNodeId;
    private final UUID childNodeId;

    private int position;

    private final Instant createdAt;

    private KnowledgeBranch(
            UUID parentNodeId,
            UUID childNodeId,
            int position,
            Instant createdAt
    ) {
        this.parentNodeId = parentNodeId;
        this.childNodeId = childNodeId;
        this.position = position;
        this.createdAt = createdAt;
    }

    public static KnowledgeBranch create(
            KnowledgeNode parent,
            KnowledgeNode child,
            int position
    ) {
        if (parent.getId().equals(child.getId())) {
            throw new BusinessException(ErrorCode.INVALID_KNOWLEDGE_BRANCH);
        }

        return new KnowledgeBranch(
                parent.getId(),
                child.getId(),
                position,
                null
        );
    }

    public static KnowledgeBranch restore(
            UUID parentNodeId,
            UUID childNodeId,
            int position,
            Instant createdAt
    ) {
        return new KnowledgeBranch(
                parentNodeId,
                childNodeId,
                position,
                createdAt
        );
    }

    public void moveTo(int position) {
        this.position = position;
    }
}
