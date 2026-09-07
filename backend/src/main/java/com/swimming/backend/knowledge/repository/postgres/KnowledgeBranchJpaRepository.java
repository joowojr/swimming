package com.swimming.backend.knowledge.repository.postgres;

import com.swimming.backend.knowledge.repository.postgres.entity.KnowledgeBranchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KnowledgeBranchJpaRepository extends JpaRepository<KnowledgeBranchEntity, Long> {

    Optional<KnowledgeBranchEntity> findByParentNodeIdAndChildNodeId(
            UUID parentNodeId,
            UUID childNodeId
    );

    List<KnowledgeBranchEntity> findAllByParentNodeIdOrderByPositionAsc(UUID parentNodeId);

    List<KnowledgeBranchEntity> findAllByChildNodeIdOrderByPositionAsc(UUID childNodeId);

    void deleteByParentNodeIdAndChildNodeId(UUID parentNodeId, UUID childNodeId);
}
