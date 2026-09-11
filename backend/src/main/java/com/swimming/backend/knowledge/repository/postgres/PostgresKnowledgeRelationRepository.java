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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PostgresKnowledgeRelationRepository implements KnowledgeRelationRepository {

    private final KnowledgeRelationJpaRepository jpaRepository;

    /**
     * 자연키가 같은 Relation이 있으면 근거를 갱신하고 없으면 새로 만든다.
     *
     * <p>(fromNodeId, relationType) 묶음마다 기존 행을 한 번에 읽고, 갱신 대상과 신규 대상을
     * 함께 {@code saveAll}로 넘긴다. 한 건씩 조회하면 Source 하나를 저장할 때마다 Subject 수만큼
     * 왕복이 생긴다.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public List<KnowledgeRelation> saveAll(List<KnowledgeRelation> relations) {
        if (relations.isEmpty()) {
            return List.of();
        }

        return relations.stream()
                .collect(Collectors.groupingBy(
                        relation -> new Lookup(relation.getFromNodeId(), relation.getRelationType()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ))
                .entrySet().stream()
                .flatMap(group -> saveGroup(group.getKey(), group.getValue()).stream())
                .toList();
    }

    /**
     * from과 relationType이 같은 묶음 하나를 조회 한 번과 저장 한 번으로 처리한다.
     *
     * <p>묶음 안에서는 자연키 가운데 toNodeId만 달라지므로 그것으로 기존 행과 맞춘다. 같은
     * toNodeId가 여러 번 들어오면 마지막 관찰만 남긴다. 한 건씩 저장하던 때는 뒤에 온 것이
     * 앞의 것을 덮었고, 한 번에 저장하면서 중복을 그대로 두면 같은 자연키로 두 행을 만들어
     * 유니크 제약에 걸린다.
     */
    private List<KnowledgeRelation> saveGroup(Lookup lookup, List<KnowledgeRelation> group) {
        Map<UUID, KnowledgeRelation> requested = new LinkedHashMap<>();
        group.forEach(relation -> requested.put(relation.getToNodeId(), relation));

        Map<UUID, KnowledgeRelationEntity> existing = jpaRepository
                .findAllByFromNodeIdAndToNodeIdInAndRelationType(
                        lookup.fromNodeId(), requested.keySet(), lookup.relationType()
                )
                .stream()
                .collect(Collectors.toMap(
                        KnowledgeRelationEntity::getToNodeId, entity -> entity
                ));

        List<KnowledgeRelationEntity> entities = requested.values().stream()
                .map(relation -> merge(existing.get(relation.getToNodeId()), relation))
                .toList();

        return jpaRepository.saveAll(entities).stream()
                .map(PostgresKnowledgeRelationRepository::toDomain)
                .toList();
    }

    private KnowledgeRelationEntity merge(KnowledgeRelationEntity existing, KnowledgeRelation relation) {
        if (existing == null) {
            return KnowledgeRelationEntity.builder()
                    .fromNodeId(relation.getFromNodeId())
                    .toNodeId(relation.getToNodeId())
                    .relationType(relation.getRelationType())
                    .origin(relation.getOrigin())
                    .confidence(relation.getConfidence())
                    .evidence(relation.getEvidence())
                    .build();
        }

        existing.reinforce(
                relation.getOrigin(),
                relation.getConfidence(),
                relation.getEvidence()
        );
        return existing;
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

    private record Lookup(UUID fromNodeId, RelationType relationType) {
    }
}
