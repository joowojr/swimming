package com.swimming.backend.knowledge.repository.postgres;

import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.repository.postgres.entity.KnowledgeNodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

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

    Optional<KnowledgeNodeEntity> findByUserIdAndNodeTypeAndNormalizedTitleAndDeletedFalse(
            Long userId,
            NodeType nodeType,
            String normalizedTitle
    );
}
