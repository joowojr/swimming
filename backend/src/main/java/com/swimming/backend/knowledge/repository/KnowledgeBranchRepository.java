package com.swimming.backend.knowledge.repository;

import com.swimming.backend.knowledge.domain.KnowledgeBranch;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Branch는 (parentNodeId, childNodeId) 자연키로 주소를 지정한다.
 * 저장소가 발급한 식별자를 밖으로 내보내지 않는다.
 */
public interface KnowledgeBranchRepository {

    KnowledgeBranch save(KnowledgeBranch branch);

    Optional<KnowledgeBranch> find(UUID parentNodeId, UUID childNodeId);

    List<KnowledgeBranch> findAllByParentNodeId(UUID parentNodeId);

    List<KnowledgeBranch> findAllByChildNodeId(UUID childNodeId);

    void delete(UUID parentNodeId, UUID childNodeId);
}
