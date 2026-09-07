package com.swimming.backend.knowledge.repository.postgres;

import com.swimming.backend.knowledge.domain.KnowledgeBranch;
import com.swimming.backend.knowledge.repository.KnowledgeBranchRepository;
import com.swimming.backend.knowledge.repository.postgres.entity.KnowledgeBranchEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresKnowledgeBranchRepository implements KnowledgeBranchRepository {

    private final KnowledgeBranchJpaRepository jpaRepository;

    /**
     * 자연키가 같은 Branch가 있으면 갱신하고 없으면 새로 만든다.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public KnowledgeBranch save(KnowledgeBranch branch) {
        KnowledgeBranchEntity entity = jpaRepository
                .findByParentNodeIdAndChildNodeId(
                        branch.getParentNodeId(),
                        branch.getChildNodeId()
                )
                .orElseGet(() -> KnowledgeBranchEntity.builder()
                        .parentNodeId(branch.getParentNodeId())
                        .childNodeId(branch.getChildNodeId())
                        .position(branch.getPosition())
                        .build());

        entity.moveTo(branch.getPosition());

        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<KnowledgeBranch> find(UUID parentNodeId, UUID childNodeId) {
        return jpaRepository.findByParentNodeIdAndChildNodeId(parentNodeId, childNodeId)
                .map(PostgresKnowledgeBranchRepository::toDomain);
    }

    @Override
    public List<KnowledgeBranch> findAllByParentNodeId(UUID parentNodeId) {
        return jpaRepository.findAllByParentNodeIdOrderByPositionAsc(parentNodeId)
                .stream()
                .map(PostgresKnowledgeBranchRepository::toDomain)
                .toList();
    }

    @Override
    public List<KnowledgeBranch> findAllByChildNodeId(UUID childNodeId) {
        return jpaRepository.findAllByChildNodeIdOrderByPositionAsc(childNodeId)
                .stream()
                .map(PostgresKnowledgeBranchRepository::toDomain)
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void delete(UUID parentNodeId, UUID childNodeId) {
        jpaRepository.deleteByParentNodeIdAndChildNodeId(parentNodeId, childNodeId);
    }

    private static KnowledgeBranch toDomain(KnowledgeBranchEntity entity) {
        return KnowledgeBranch.restore(
                entity.getParentNodeId(),
                entity.getChildNodeId(),
                entity.getPosition(),
                entity.getCreatedAt()
        );
    }
}
