package com.swimming.backend.knowledge.repository.postgres;

import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.repository.KnowledgeNodeRepository;
import com.swimming.backend.knowledge.repository.postgres.entity.KnowledgeNodeEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresKnowledgeNodeRepository implements KnowledgeNodeRepository {

    private final KnowledgeNodeJpaRepository jpaRepository;

    @Override
    public KnowledgeNode save(KnowledgeNode node) {
        return toDomain(jpaRepository.save(toEntity(node)));
    }

    @Override
    public Optional<KnowledgeNode> findById(UUID id) {
        return jpaRepository.findByIdAndDeletedFalse(id).map(PostgresKnowledgeNodeRepository::toDomain);
    }

    @Override
    public Optional<KnowledgeNode> findByIdAndUserId(UUID id, Long userId) {
        return jpaRepository.findByIdAndUserIdAndDeletedFalse(id, userId)
                .map(PostgresKnowledgeNodeRepository::toDomain);
    }

    @Override
    public List<KnowledgeNode> findAllByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }

        return jpaRepository.findAllByIdInAndDeletedFalse(ids)
                .stream()
                .map(PostgresKnowledgeNodeRepository::toDomain)
                .toList();
    }

    @Override
    public List<KnowledgeNode> findAllByUserIdAndNodeType(Long userId, NodeType nodeType) {
        return jpaRepository.findAllByUserIdAndNodeTypeAndDeletedFalseOrderByCreatedAtDesc(userId, nodeType)
                .stream()
                .map(PostgresKnowledgeNodeRepository::toDomain)
                .toList();
    }

    @Override
    public Optional<KnowledgeNode> findByUserIdAndNodeTypeAndNormalizedTitle(
            Long userId,
            NodeType nodeType,
            String normalizedTitle
    ) {
        return jpaRepository.findByUserIdAndNodeTypeAndNormalizedTitleAndDeletedFalse(userId, nodeType, normalizedTitle)
                .map(PostgresKnowledgeNodeRepository::toDomain);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    static KnowledgeNodeEntity toEntity(KnowledgeNode node) {
        return KnowledgeNodeEntity.builder()
                .id(node.getId())
                .userId(node.getUserId())
                .nodeType(node.getNodeType())
                .title(node.getTitle())
                .normalizedTitle(node.getNormalizedTitle())
                .description(node.getDescription())
                .deleted(node.isDeleted())
                .build();
    }

    static KnowledgeNode toDomain(KnowledgeNodeEntity entity) {
        return KnowledgeNode.restore(
                entity.getId(),
                entity.getUserId(),
                entity.getNodeType(),
                entity.getTitle(),
                entity.getDescription(),
                entity.isDeleted(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
