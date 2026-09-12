package com.swimming.backend.knowledge.repository.postgres;

import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.repository.KnowledgeNodeRepository;
import com.swimming.backend.knowledge.repository.postgres.entity.KnowledgeNodeEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresKnowledgeNodeRepository implements KnowledgeNodeRepository {

    private final KnowledgeNodeJpaRepository jpaRepository;
    private final JdbcTemplate jdbcTemplate;

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
    public List<KnowledgeNode> findAllByNormalizedTitles(
            Long userId,
            NodeType nodeType,
            Collection<String> normalizedTitles
    ) {
        if (normalizedTitles.isEmpty()) {
            return List.of();
        }

        return jpaRepository
                .findAllByUserIdAndNodeTypeAndNormalizedTitleInAndDeletedFalse(
                        userId, nodeType, normalizedTitles
                )
                .stream()
                .map(PostgresKnowledgeNodeRepository::toDomain)
                .toList();
    }

    @Override
    public KnowledgeNode createSubjectWithEmbedding(
            KnowledgeNode subject,
            float[] titleEmbedding,
            String embeddingModel
    ) {
        if (subject.getNodeType() != NodeType.SUBJECT) {
            throw new IllegalArgumentException("title embedding can only be stored for a subject");
        }

        return jdbcTemplate.queryForObject(
                """
                insert into knowledge_node (
                    id, user_id, node_type, title, normalized_title, description,
                    is_deleted, title_embedding, title_embedding_model,
                    created_at, updated_at
                ) values (?, ?, 'SUBJECT', ?, ?, ?, false, cast(? as vector), ?,
                          current_timestamp, current_timestamp)
                returning created_at, updated_at
                """,
                (resultSet, rowNumber) -> KnowledgeNode.restore(
                        subject.getId(),
                        subject.getUserId(),
                        subject.getNodeType(),
                        subject.getTitle(),
                        subject.getDescription(),
                        false,
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at").toInstant()
                ),
                subject.getId(),
                subject.getUserId(),
                subject.getTitle(),
                subject.getNormalizedTitle(),
                subject.getDescription(),
                vectorLiteral(titleEmbedding),
                embeddingModel
        );
    }

    @Override
    public int softDeleteAllOwnedByIds(Long userId, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        return jpaRepository.softDeleteAllOwnedByIds(userId, ids);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    private String vectorLiteral(float[] embedding) {
        StringBuilder literal = new StringBuilder("[");
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) {
                literal.append(',');
            }
            literal.append(Float.toString(embedding[index]));
        }
        return literal.append(']').toString();
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
