package com.swimming.backend.knowledge.repository.postgres.entity;

import com.swimming.backend.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(
        name = "knowledge_branch",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_knowledge_branch",
                        columnNames = {"parent_node_id", "child_node_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_knowledge_branch_parent",
                        columnList = "parent_node_id"
                ),
                @Index(
                        name = "idx_knowledge_branch_child",
                        columnList = "child_node_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KnowledgeBranchEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_node_id", nullable = false)
    private UUID parentNodeId;

    @Column(name = "child_node_id", nullable = false)
    private UUID childNodeId;

    @Column(name = "position", nullable = false)
    private int position;

    @Builder
    private KnowledgeBranchEntity(
            UUID parentNodeId,
            UUID childNodeId,
            int position
    ) {
        this.parentNodeId = parentNodeId;
        this.childNodeId = childNodeId;
        this.position = position;
    }

    public void moveTo(int position) {
        this.position = position;
    }
}
