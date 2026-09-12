package com.swimming.backend.knowledge.repository.postgres;

import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.repository.postgres.entity.KnowledgeNodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 조회는 모두 지운 노드를 거른다. 지운 것이 그래프나 목록에 되살아나지 않게 한다. */
public interface KnowledgeNodeJpaRepository extends JpaRepository<KnowledgeNodeEntity, UUID> {

    Optional<KnowledgeNodeEntity> findByIdAndDeletedFalse(UUID id);

    Optional<KnowledgeNodeEntity> findByIdAndUserIdAndDeletedFalse(UUID id, Long userId);

    List<KnowledgeNodeEntity> findAllByIdInAndDeletedFalse(Collection<UUID> ids);

    List<KnowledgeNodeEntity> findAllByUserIdAndNodeTypeAndDeletedFalseOrderByCreatedAtDesc(
            Long userId,
            NodeType nodeType
    );

    List<KnowledgeNodeEntity> findAllByUserIdAndNodeTypeAndNormalizedTitleInAndDeletedFalse(
            Long userId,
            NodeType nodeType,
            Collection<String> normalizedTitles
    );

    Optional<KnowledgeNodeEntity> findByUserIdAndNodeTypeAndNormalizedTitleAndDeletedFalse(
            Long userId,
            NodeType nodeType,
            String normalizedTitle
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update KnowledgeNodeEntity n
               set n.deleted = true,
                   n.updatedAt = instant
             where n.userId = :userId
               and n.id in :ids
               and n.deleted = false
            """)
    int softDeleteAllOwnedByIds(
            @Param("userId") Long userId,
            @Param("ids") Collection<UUID> ids
    );
}
