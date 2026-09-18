package com.swimming.backend.knowledge.repository.postgres;

import com.swimming.backend.knowledge.domain.RelationType;
import com.swimming.backend.knowledge.repository.postgres.entity.KnowledgeRelationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * 이 노드들에서 나가는 같은 타입의 관계를 한 번에 지운다.
     *
     * <p>대상 Entity를 읽지 않고 조건으로 지우고 영향 행 수를 돌려준다. 묶음을 합치거나
     * 통째로 갈아엎을 때 사라지는 쪽의 간선을 걷어내는 데 쓴다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from KnowledgeRelationEntity relation
            where relation.fromNodeId in :fromNodeIds
              and relation.relationType = :relationType
            """)
    int deleteAllFrom(
            @Param("fromNodeIds") Collection<UUID> fromNodeIds,
            @Param("relationType") RelationType relationType
    );

    void deleteByFromNodeIdAndToNodeIdAndRelationType(
            UUID fromNodeId,
            UUID toNodeId,
            RelationType relationType
    );
}
