package com.swimming.backend.knowledge.repository.postgres;

import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.SourceProcessingStatus;
import com.swimming.backend.knowledge.repository.KnowledgeSourceRepository;
import com.swimming.backend.knowledge.repository.SourcePageQuery;
import com.swimming.backend.knowledge.repository.SourceSearchPageQuery;
import com.swimming.backend.knowledge.repository.postgres.entity.KnowledgeNodeEntity;
import com.swimming.backend.knowledge.repository.postgres.entity.KnowledgeSourceEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * SOURCE 애그리거트를 knowledge_node와 knowledge_source 두 테이블로 나누어 저장한다.
 */
@Repository
@RequiredArgsConstructor
public class PostgresKnowledgeSourceRepository implements KnowledgeSourceRepository {

    private final KnowledgeNodeJpaRepository nodeJpaRepository;
    private final KnowledgeSourceJpaRepository sourceJpaRepository;

    @Override
    public KnowledgeSource save(KnowledgeSource source) {
        KnowledgeNodeEntity nodeEntity = nodeJpaRepository.save(
                PostgresKnowledgeNodeRepository.toEntity(source.getNode())
        );
        KnowledgeSourceEntity sourceEntity = sourceJpaRepository.save(toEntity(source));

        return toDomain(nodeEntity, sourceEntity);
    }

    @Override
    public void updateReadAt(UUID sourceId, Instant readAt){
        KnowledgeSourceEntity entity = sourceJpaRepository.findById(sourceId).orElse(null);
        if (entity != null){
            entity.updateReadAt(readAt);
            sourceJpaRepository.flush();
        }
    }

    @Override
    public void updateStatus(
            UUID sourceId,
            SourceProcessingStatus status,
            String failureMessage,
            boolean retryable
    ) {
        KnowledgeSourceEntity entity = sourceJpaRepository.findById(sourceId).orElse(null);
        if (entity != null){
            entity.updateStatus(status, failureMessage, retryable);
            sourceJpaRepository.flush();
        }
    }


    @Override
    public Optional<KnowledgeSource> findById(UUID nodeId) {
        return sourceJpaRepository.findById(nodeId)
                .flatMap(sourceEntity -> nodeJpaRepository.findByIdAndDeletedFalse(nodeId)
                        .map(nodeEntity -> toDomain(nodeEntity, sourceEntity)));
    }

    @Override
    public List<KnowledgeSource> findAllByIds(Collection<UUID> nodeIds) {
        if (nodeIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, KnowledgeNodeEntity> nodesById = nodeJpaRepository.findAllByIdInAndDeletedFalse(nodeIds)
                .stream()
                .collect(Collectors.toMap(
                        KnowledgeNodeEntity::getId,
                        Function.identity()
                ));

        return sourceJpaRepository.findAllByNodeIdIn(nodeIds)
                .stream()
                .filter(sourceEntity -> nodesById.containsKey(sourceEntity.getNodeId()))
                .map(sourceEntity -> toDomain(
                        nodesById.get(sourceEntity.getNodeId()),
                        sourceEntity
                ))
                .toList();
    }

    @Override
    public void saveSummaryEmbedding(
            Long userId,
            UUID sourceId,
            float[] summaryEmbedding,
            String embeddingModel
    ) {
        int updated = sourceJpaRepository.updateSummaryEmbedding(
                userId, sourceId, vectorLiteral(summaryEmbedding), embeddingModel
        );
        if (updated != 1) {
            throw new IllegalStateException("failed to update source summary embedding: " + sourceId);
        }
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

    @Override
    public List<KnowledgeSource> findAllActiveByIds(Long userId, Collection<UUID> nodeIds) {
        if (nodeIds.isEmpty()) {
            return List.of();
        }

        return sourceJpaRepository.findAllActiveByIds(userId, nodeIds)
                .stream()
                .map(row -> toDomain(row.getNode(), row.getSource()))
                .toList();
    }

    @Override
    public List<KnowledgeSource> findAllActiveInFolderByIds(
            Long userId,
            Long folderId,
            Collection<UUID> nodeIds
    ) {
        if (nodeIds.isEmpty()) {
            return List.of();
        }

        return sourceJpaRepository.findAllActiveInFolderByIds(userId, folderId, nodeIds)
                .stream()
                .map(row -> toDomain(row.getNode(), row.getSource()))
                .toList();
    }

    @Override
    public List<UUID> findAliveNodeIdsInFolder(Long userId, Long folderId) {
        return sourceJpaRepository.findAliveNodeIdsInFolder(userId, folderId);
    }

    @Override
    public List<KnowledgeSource> findAllCategorizationTargets(
            Long userId,
            Long folderId,
            Collection<UUID> nodeIds
    ) {
        if (nodeIds.isEmpty()) {
            return List.of();
        }

        return sourceJpaRepository.findAllCategorizationTargetsInFolderByIds(
                        userId, folderId, SourceProcessingStatus.COMPLETED, nodeIds
                )
                .stream()
                .map(row -> toDomain(row.getNode(), row.getSource()))
                .toList();
    }

    @Override
    public List<KnowledgeSource> findAllInFolderByCanonicalUrls(
            Long userId,
            Long folderId,
            Collection<String> canonicalUrls
    ) {
        if (canonicalUrls.isEmpty()) {
            return List.of();
        }

        return sourceJpaRepository.findAllActiveInFolderByCanonicalUrls(
                        userId, folderId, canonicalUrls
                )
                .stream()
                .map(row -> toDomain(row.getNode(), row.getSource()))
                .toList();
    }

    @Override
    public boolean existsInFolder(Long userId, Long folderId) {
        return !sourceJpaRepository.findAnyActiveInFolder(userId, folderId, PageRequest.of(0, 1)).isEmpty();
    }

    @Override
    public List<KnowledgeSource> findPage(SourcePageQuery query) {
        List<KnowledgeSourceEntity> sourceEntities = query.cursorCreatedAt() == null
                ? sourceJpaRepository.findFirstPage(
                        query.userId(),
                        query.folderId(),
                        query.status(),
                        PageRequest.of(0, query.limit())
                )
                : sourceJpaRepository.findNextPage(
                        query.userId(),
                        query.folderId(),
                        query.status(),
                        query.cursorCreatedAt(),
                        query.cursorNodeId(),
                        PageRequest.of(0, query.limit())
                );

        if (sourceEntities.isEmpty()) {
            return List.of();
        }

        Map<UUID, KnowledgeNodeEntity> nodesById = nodeJpaRepository
                .findAllByIdInAndDeletedFalse(
                        sourceEntities.stream().map(KnowledgeSourceEntity::getNodeId).toList()
                )
                .stream()
                .collect(Collectors.toMap(KnowledgeNodeEntity::getId, Function.identity()));

        // 정렬은 쿼리가 이미 했다. 여기서 순서를 다시 만들지 않는다.
        return sourceEntities.stream()
                .filter(sourceEntity -> nodesById.containsKey(sourceEntity.getNodeId()))
                .map(sourceEntity -> toDomain(nodesById.get(sourceEntity.getNodeId()), sourceEntity))
                .toList();
    }

    @Override
    public List<KnowledgeSource> findSearchPage(SourceSearchPageQuery query) {
        boolean filterFolder = query.folderId() != null;
        boolean filterSources = query.sourceIds() != null;
        Long folderId = filterFolder ? query.folderId() : 0L;
        Collection<UUID> sourceIds = filterSources
                ? query.sourceIds()
                : List.of(new UUID(0L, 0L));

        List<KnowledgeSourceEntity> sourceEntities = query.cursorCreatedAt() == null
                ? sourceJpaRepository.findFirstSearchPage(
                        query.userId(), filterFolder, folderId, filterSources, sourceIds,
                        PageRequest.of(0, query.limit())
                )
                : sourceJpaRepository.findNextSearchPage(
                        query.userId(), filterFolder, folderId, filterSources, sourceIds,
                        query.cursorCreatedAt(), query.cursorNodeId(),
                        PageRequest.of(0, query.limit())
                );

        return toDomains(sourceEntities);
    }

    private List<KnowledgeSource> toDomains(List<KnowledgeSourceEntity> sourceEntities) {
        if (sourceEntities.isEmpty()) {
            return List.of();
        }

        Map<UUID, KnowledgeNodeEntity> nodesById = nodeJpaRepository
                .findAllByIdInAndDeletedFalse(
                        sourceEntities.stream().map(KnowledgeSourceEntity::getNodeId).toList()
                )
                .stream()
                .collect(Collectors.toMap(KnowledgeNodeEntity::getId, Function.identity()));

        return sourceEntities.stream()
                .filter(sourceEntity -> nodesById.containsKey(sourceEntity.getNodeId()))
                .map(sourceEntity -> toDomain(nodesById.get(sourceEntity.getNodeId()), sourceEntity))
                .toList();
    }

    private KnowledgeSourceEntity toEntity(KnowledgeSource source) {
        return KnowledgeSourceEntity.builder()
                .nodeId(source.getId())
                .folderId(source.getFolderId())
                .title(source.getNode().getTitle())
                .url(source.getUrl())
                .canonicalUrl(source.getCanonicalUrl())
                .content(source.getContent())
                .summary(source.getSummary())
                .sourceType(source.getSourceType())
                .author(source.getAuthor())
                .publishedAt(source.getPublishedAt())
                .processingStatus(source.getProcessingStatus())
                .analysisVersion(source.getAnalysisVersion())
                .failureMessage(source.getFailureMessage())
                .retryable(source.isRetryable())
                .readAt(source.getReadAt())
                .build();
    }

    private KnowledgeSource toDomain(
            KnowledgeNodeEntity nodeEntity,
            KnowledgeSourceEntity sourceEntity
    ) {
        KnowledgeNode node = PostgresKnowledgeNodeRepository.toDomain(nodeEntity);

        return KnowledgeSource.restore(
                node,
                sourceEntity.getFolderId(),
                sourceEntity.getUrl(),
                sourceEntity.getCanonicalUrl(),
                sourceEntity.getContent(),
                sourceEntity.getSummary(),
                sourceEntity.getSourceType(),
                sourceEntity.getAuthor(),
                sourceEntity.getPublishedAt(),
                sourceEntity.getProcessingStatus(),
                sourceEntity.getAnalysisVersion(),
                sourceEntity.getFailureMessage(),
                sourceEntity.isRetryable(),
                sourceEntity.getReadAt()
        );
    }
}
