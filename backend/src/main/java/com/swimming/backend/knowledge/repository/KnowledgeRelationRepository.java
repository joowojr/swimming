package com.swimming.backend.knowledge.repository;

import com.swimming.backend.knowledge.domain.KnowledgeRelation;
import com.swimming.backend.knowledge.domain.RelationType;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Relation은 (fromNodeId, toNodeId, relationType) 자연키로 주소를 지정한다.
 * 저장소가 발급한 식별자를 밖으로 내보내지 않는다.
 */
public interface KnowledgeRelationRepository {

    /**
     * 자연키가 같은 Relation이 있으면 근거를 갱신하고 없으면 새로 만든다.
     *
     * <p>(fromNodeId, relationType)이 같은 것끼리 묶어 조회를 한 번으로 줄인다. 같은 자연키가
     * 여러 번 들어오면 마지막 관찰만 남는다.
     */
    List<KnowledgeRelation> saveAll(List<KnowledgeRelation> relations);

    List<KnowledgeRelation> findAllByFromNodeIdIn(
            Collection<UUID> fromNodeIds,
            Collection<RelationType> relationTypes
    );

    List<KnowledgeRelation> findAllByToNodeIdIn(
            Collection<UUID> toNodeIds,
            Collection<RelationType> relationTypes
    );

    void delete(UUID fromNodeId, UUID toNodeId, RelationType relationType);

    /** 한 노드에서 나가는 같은 타입의 관계를 모두 지우고 지운 수를 돌려준다. */
    int deleteAllFrom(UUID fromNodeId, RelationType relationType);
}
