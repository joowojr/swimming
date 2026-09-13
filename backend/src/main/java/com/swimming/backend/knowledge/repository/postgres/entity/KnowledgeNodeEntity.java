package com.swimming.backend.knowledge.repository.postgres.entity;

import com.swimming.backend.common.entity.BaseTimeEntity;
import com.swimming.backend.knowledge.domain.NodeType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(
        name = "knowledge_node",
        indexes = {
                @Index(
                        name = "idx_knowledge_node_user_type_deleted",
                        columnList = "user_id, node_type, is_deleted"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KnowledgeNodeEntity extends BaseTimeEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false, length = 20)
    private NodeType nodeType;

    @Column(nullable = false, length = 500)
    private String title;

    /** 표기 차이를 걷어낸 title. SUBJECT / TOPIC은 이 값으로 중복을 막는다. */
    @Column(name = "normalized_title", nullable = false, length = 500)
    private String normalizedTitle;

    @Column(columnDefinition = "text")
    private String description;

    /** 지운 노드는 행을 남기고 모든 조회에서 빠진다. */
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Builder
    private KnowledgeNodeEntity(
            UUID id,
            Long userId,
            NodeType nodeType,
            String title,
            String normalizedTitle,
            String description,
            boolean deleted
    ) {
        this.id = id;
        this.userId = userId;
        this.nodeType = nodeType;
        this.title = title;
        this.normalizedTitle = normalizedTitle;
        this.description = description;
        this.deleted = deleted;
    }

    public void delete() {
        this.deleted = true;
    }
}
