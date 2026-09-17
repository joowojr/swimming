package com.swimming.backend.knowledge.domain;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class KnowledgeNode {

    private final UUID id;
    private final Long userId;
    private final NodeType nodeType;

    private String title;

    /** 사용자가 제목을 마지막으로 수정한 시각. 수정 전에는 null이다. */
    private Instant titleRenamedAt;

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
            Instant updatedAt,
            Instant titleRenamedAt
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
        this.titleRenamedAt = titleRenamedAt;
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
            Instant updatedAt,
            Instant titleRenamedAt
    ) {
        return new KnowledgeNode(
                id,
                userId,
                nodeType,
                title,
                description,
                deleted,
                createdAt,
                updatedAt,
                titleRenamedAt
        );
    }

    public void rename(String title) {
        this.title = title;
        this.normalizedTitle = NodeTitleNormalizer.normalize(title);
    }

    /** 사용자 이름 수정은 Category와 Topic에만 허용한다. */
    public void renameByUser(String title, Instant renamedAt) {
        if (nodeType != NodeType.CATEGORY && nodeType != NodeType.TOPIC) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_NODE_TITLE_NOT_EDITABLE);
        }
        String strippedTitle = title.strip();
        if (!this.title.equals(strippedTitle)) {
            rename(strippedTitle);
            this.titleRenamedAt = renamedAt;
        }
    }

    public void updateDescription(String description) {
        this.description = description;
    }

    public void delete() {
        this.deleted = true;
    }
}
