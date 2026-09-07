package com.swimming.backend.knowledge.repository.postgres;

import com.swimming.backend.knowledge.domain.KnowledgeRelation;
import com.swimming.backend.knowledge.domain.RelationType;
import com.swimming.backend.knowledge.repository.KnowledgeRelationRepository;
import com.swimming.backend.knowledge.repository.postgres.entity.KnowledgeRelationEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresKnowledgeRelationRepository implements KnowledgeRelationRepository {

    private final KnowledgeRelationJpaRepository jpaRepository;

    /**
     * 자연키가 같은 Relation이 있으면 근거를 갱신하고 없으면 새로 만든다.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public KnowledgeRelation save(KnowledgeRelation relation) {
        KnowledgeRelationEntity entity = jpaRepository
                .findByFromNodeIdAndToNodeIdAndRelationType(
                        relation.getFromNodeId(),
                        relation.getToNodeId(),
                        relation.getRelationType()
                )
                .orElseGet(() -> KnowledgeRelationEntity.builder()
                        .fromNodeId(relation.getFromNodeId())
                        .toNodeId(relation.getToNodeId())
                        .relationType(relation.getRelationType())
                        .origin(relation.getOrigin())
                        .confidence(relation.getConfidence())
                        .evidence(relation.getEvidence())
                        .build());

        entity.reinforce(
                relation.getOrigin(),
                relation.getConfidence(),
                relation.getEvidence()
        );

        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<KnowledgeRelation> find(
            UUID fromNodeId,
            UUID toNodeId,
            RelationType relationType
    ) {
        return jpaRepository
                .findByFromNodeIdAndToNodeIdAndRelationType(fromNodeId, toNodeId, relationType)
                .map(PostgresKnowledgeRelationRepository::toDomain);
    }

    @Override
    public List<KnowledgeRelation> findAllByFromNodeIdIn(
            Collection<UUID> fromNodeIds,
            Collection<RelationType> relationTypes
    ) {
        if (fromNodeIds.isEmpty() || relationTypes.isEmpty()) {
            return List.of();
        }

        return jpaRepository
                .findAllByFromNodeIdInAndRelationTypeIn(fromNodeIds, relationTypes)
                .stream()
                .map(PostgresKnowledgeRelationRepository::toDomain)
                .toList();
    }

    @Override
    public List<KnowledgeRelation> findAllByToNodeIdIn(
            Collection<UUID> toNodeIds,
            Collection<RelationType> relationTypes
    ) {
        if (toNodeIds.isEmpty() || relationTypes.isEmpty()) {
            return List.of();
        }

        return jpaRepository
                .findAllByToNodeIdInAndRelationTypeIn(toNodeIds, relationTypes)
                .stream()
                .map(PostgresKnowledgeRelationRepository::toDomain)
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void delete(UUID fromNodeId, UUID toNodeId, RelationType relationType) {
        jpaRepository.deleteByFromNodeIdAndToNodeIdAndRelationType(
                fromNodeId,
                toNodeId,
                relationType
        );
    }

    private static KnowledgeRelation toDomain(KnowledgeRelationEntity entity) {
        return KnowledgeRelation.restore(
                entity.getFromNodeId(),
                entity.getToNodeId(),
                entity.getRelationType(),
                entity.getOrigin(),
                entity.getConfidence(),
                entity.getEvidence(),
                entity.getCreatedAt()
        );
    }
}
