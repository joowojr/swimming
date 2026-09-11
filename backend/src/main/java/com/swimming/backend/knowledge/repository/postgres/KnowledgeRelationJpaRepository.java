package com.swimming.backend.knowledge.repository.postgres;

import com.swimming.backend.knowledge.domain.RelationType;
import com.swimming.backend.knowledge.repository.postgres.entity.KnowledgeRelationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface KnowledgeRelationJpaRepository extends JpaRepository<KnowledgeRelationEntity, Long> {

    List<KnowledgeRelationEntity> findAllByFromNodeIdAndToNodeIdInAndRelationType(
            UUID fromNodeId,
            Collection<UUID> toNodeIds,
            RelationType relationType
    );

    List<KnowledgeRelationEntity> findAllByFromNodeIdInAndRelationTypeIn(
            Collection<UUID> fromNodeIds,
            Collection<RelationType> relationTypes
    );

    List<KnowledgeRelationEntity> findAllByToNodeIdInAndRelationTypeIn(
            Collection<UUID> toNodeIds,
            Collection<RelationType> relationTypes
    );

    void deleteByFromNodeIdAndToNodeIdAndRelationType(
            UUID fromNodeId,
            UUID toNodeId,
            RelationType relationType
    );
}
