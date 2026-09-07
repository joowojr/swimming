package com.swimming.backend.knowledge.repository;

import com.swimming.backend.knowledge.domain.KnowledgeRelation;
import com.swimming.backend.knowledge.domain.RelationType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Relation은 (fromNodeId, toNodeId, relationType) 자연키로 주소를 지정한다.
 * 저장소가 발급한 식별자를 밖으로 내보내지 않는다.
 */
public interface KnowledgeRelationRepository {

    KnowledgeRelation save(KnowledgeRelation relation);

    Optional<KnowledgeRelation> find(
            UUID fromNodeId,
            UUID toNodeId,
            RelationType relationType
    );

    List<KnowledgeRelation> findAllByFromNodeIdIn(
            Collection<UUID> fromNodeIds,
            Collection<RelationType> relationTypes
    );

    List<KnowledgeRelation> findAllByToNodeIdIn(
            Collection<UUID> toNodeIds,
            Collection<RelationType> relationTypes
    );

    void delete(UUID fromNodeId, UUID toNodeId, RelationType relationType);
}
