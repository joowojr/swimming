package com.swimming.backend.knowledge.repository.postgres.entity;

import com.swimming.backend.common.entity.BaseTimeEntity;
import com.swimming.backend.knowledge.domain.RelationOrigin;
import com.swimming.backend.knowledge.domain.RelationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Getter
@Entity
@Table(
        name = "knowledge_relation",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_knowledge_relation",
                        columnNames = {"from_node_id", "to_node_id", "relation_type"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_knowledge_relation_from_type",
                        columnList = "from_node_id, relation_type"
                ),
                @Index(
                        name = "idx_knowledge_relation_to_type",
                        columnList = "to_node_id, relation_type"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KnowledgeRelationEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_node_id", nullable = false)
    private UUID fromNodeId;

    @Column(name = "to_node_id", nullable = false)
    private UUID toNodeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 20)
    private RelationType relationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RelationOrigin origin;

    @Column
    private Double confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String evidence;

    @Builder
    private KnowledgeRelationEntity(
            UUID fromNodeId,
            UUID toNodeId,
            RelationType relationType,
            RelationOrigin origin,
            Double confidence,
            String evidence
    ) {
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
        this.relationType = relationType;
        this.origin = origin;
        this.confidence = confidence;
        this.evidence = evidence;
    }

    /**
     * 다시 관찰한 내용을 이 관계에 반영한다.
     *
     * <p>사용자가 만든 관계에 AI 관찰이 오면 아무것도 하지 않는다. 사람이 직접 이은 것을
     * 자동 관찰이 덮지 않게 한다.
     */
    public void applyObservation(
            RelationOrigin origin,
            Double confidence,
            String evidence
    ) {
        if (this.origin == RelationOrigin.USER && origin != RelationOrigin.USER) {
            return;
        }

        this.origin = origin;
        this.confidence = confidence;
        this.evidence = evidence;
    }
}
