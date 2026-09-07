package com.swimming.backend.knowledge.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class KnowledgeNode {

    private final UUID id;
    private final Long userId;
    private final NodeType nodeType;

    private String title;

    /**
     * 표기 차이를 걷어낸 title. 조회 키이자 중복 판정 기준이다.
     *
     * <p>저장된 값을 되읽지 않고 title에서 매번 새로 만든다. 규칙이 바뀌었을 때 두 값이
     * 조용히 갈라지지 않게 하기 위해서다.
     */
    private String normalizedTitle;

    private String description;

    /** 지운 노드는 행을 남기고 모든 조회에서 빠진다. 관계는 지우지 않는다. */
    private boolean deleted;

    private final Instant createdAt;
    private final Instant updatedAt;

    private KnowledgeNode(
            UUID id,
            Long userId,
            NodeType nodeType,
            String title,
            String description,
            boolean deleted,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.nodeType = nodeType;
        this.title = title;
        this.normalizedTitle = NodeTitleNormalizer.normalize(title);
        this.description = description;
        this.deleted = deleted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static KnowledgeNode create(
            Long userId,
            NodeType nodeType,
            String title,
            String description
    ) {
        return new KnowledgeNode(
                UUID.randomUUID(),
                userId,
                nodeType,
                title,
                description,
                false,
                null,
                null
        );
    }

    public static KnowledgeNode restore(
            UUID id,
            Long userId,
            NodeType nodeType,
            String title,
            String description,
            boolean deleted,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new KnowledgeNode(
                id,
                userId,
                nodeType,
                title,
                description,
                deleted,
                createdAt,
                updatedAt
        );
    }

    public void rename(String title) {
        this.title = title;
        this.normalizedTitle = NodeTitleNormalizer.normalize(title);
    }

    public void updateDescription(String description) {
        this.description = description;
    }

    public void delete() {
        this.deleted = true;
    }
}
