package com.swimming.backend.knowledge.repository;

import com.swimming.backend.knowledge.domain.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 포트의 메모리 구현. PostgreSQL 없이 UseCase 파이프라인을 검증한다.
 *
 * <p>도메인 객체를 그대로 들고 있지 않고 복사해 넣는다. 실제 저장소도 조회할 때마다
 * 새 객체를 돌려주므로, 저장한 뒤 원본을 고쳐도 반영되지 않아야 한다.
 */
public final class InMemoryKnowledgeRepositories {

    private InMemoryKnowledgeRepositories() {
    }

    public static class Nodes implements KnowledgeNodeRepository {

        final Map<UUID, KnowledgeNode> stored = new LinkedHashMap<>();

        @Override
        public KnowledgeNode save(KnowledgeNode node) {
            stored.put(node.getId(), copy(node));
            return copy(stored.get(node.getId()));
        }

        @Override
        public Optional<KnowledgeNode> findById(UUID id) {
            return Optional.ofNullable(stored.get(id)).filter(node -> !node.isDeleted()).map(Nodes::copy);
        }

        @Override
        public Optional<KnowledgeNode> findByIdAndUserId(UUID id, Long userId) {
            return findById(id).filter(node -> node.getUserId().equals(userId));
        }

        @Override
        public List<KnowledgeNode> findAllByIds(Collection<UUID> ids) {
            return ids.stream()
                    .map(stored::get)
                    .filter(Objects::nonNull)
                    .filter(node -> !node.isDeleted())
                    .map(Nodes::copy)
                    .toList();
        }

        @Override
        public List<KnowledgeNode> findAllByUserIdAndNodeType(Long userId, NodeType nodeType) {
            return stored.values().stream()
                    .filter(node -> node.getUserId().equals(userId) && node.getNodeType() == nodeType)
                    .filter(node -> !node.isDeleted())
                    .map(Nodes::copy)
                    .toList();
        }

        @Override
        public Optional<KnowledgeNode> findByUserIdAndNodeTypeAndNormalizedTitle(
                Long userId, NodeType nodeType, String normalizedTitle
        ) {
            return stored.values().stream()
                    .filter(node -> node.getUserId().equals(userId)
                            && node.getNodeType() == nodeType
                            && !node.isDeleted()
                            && Objects.equals(node.getNormalizedTitle(), normalizedTitle))
                    .findFirst()
                    .map(Nodes::copy);
        }

        @Override
        public void deleteById(UUID id) {
            stored.remove(id);
        }

        /**
         * 복사하면서 생성 시각을 채운다.
         *
         * <p>{@link KnowledgeNode#create}는 시각을 비워 둔다. 실제로는 저장할 때 DB가
         * 채우기 때문이다. 목록 정렬과 커서가 그 값을 쓰므로 대역도 같은 일을 해야 한다.
         */
        static KnowledgeNode copy(KnowledgeNode node) {
            Instant createdAt = node.getCreatedAt() == null ? Instant.now() : node.getCreatedAt();

            return KnowledgeNode.restore(
                    node.getId(), node.getUserId(), node.getNodeType(),
                    node.getTitle(), node.getDescription(), node.isDeleted(),
                    createdAt,
                    node.getUpdatedAt() == null ? createdAt : node.getUpdatedAt()
            );
        }
    }

    public static class Sources implements KnowledgeSourceRepository {

        final Map<UUID, KnowledgeSource> stored = new LinkedHashMap<>();
        final Map<UUID, float[]> summaryEmbeddings = new HashMap<>();
        final Map<UUID, String> summaryEmbeddingModels = new HashMap<>();

        @Override
        public KnowledgeSource save(KnowledgeSource source) {
            stored.put(source.getId(), copy(source));
            return copy(stored.get(source.getId()));
        }

        @Override
        public Optional<KnowledgeSource> findById(UUID nodeId) {
            return Optional.ofNullable(stored.get(nodeId))
                    .filter(source -> !source.isDeleted())
                    .map(Sources::copy);
        }

        @Override
        public List<KnowledgeSource> findAllByIds(Collection<UUID> nodeIds) {
            return nodeIds.stream()
                    .map(stored::get)
                    .filter(Objects::nonNull)
                    .filter(source -> !source.isDeleted())
                    .map(Sources::copy)
                    .toList();
        }

        @Override
        public void updateReadAt(UUID sourceId, Instant readAt) {
            KnowledgeSource source = stored.get(sourceId);
            if (source == null) {
                return;
            }
            if (readAt == null) {
                source.markUnread();
            } else {
                source.markRead(readAt);
            }
            stored.put(sourceId, copy(source));
        }

        @Override
        public List<UUID> findSimilarSourceIds(
                Long userId,
                UUID excludedSourceId,
                float[] summaryEmbedding,
                String embeddingModel,
                int limit
        ) {
            return stored.values().stream()
                    .filter(source -> source.getUserId().equals(userId))
                    .filter(source -> !source.getId().equals(excludedSourceId))
                    .filter(source -> !source.isDeleted())
                    .filter(source -> source.getProcessingStatus() == SourceProcessingStatus.COMPLETED)
                    .filter(source -> summaryEmbeddings.containsKey(source.getId()))
                    .filter(source -> Objects.equals(
                            summaryEmbeddingModels.get(source.getId()), embeddingModel
                    ))
                    .sorted(Comparator.comparingDouble(source -> cosineDistance(
                            summaryEmbeddings.get(source.getId()), summaryEmbedding
                    )))
                    .limit(limit)
                    .map(KnowledgeSource::getId)
                    .toList();
        }

        @Override
        public void saveSummaryEmbedding(
                Long userId,
                UUID sourceId,
                float[] summaryEmbedding,
                String embeddingModel
        ) {
            KnowledgeSource source = findById(sourceId).orElseThrow();
            if (!source.getUserId().equals(userId)) {
                throw new IllegalStateException("source owner does not match");
            }
            summaryEmbeddings.put(sourceId, summaryEmbedding.clone());
            summaryEmbeddingModels.put(sourceId, embeddingModel);
        }

        public float[] summaryEmbeddingOf(UUID sourceId) {
            float[] embedding = summaryEmbeddings.get(sourceId);
            return embedding == null ? null : embedding.clone();
        }

        public String summaryEmbeddingModelOf(UUID sourceId) {
            return summaryEmbeddingModels.get(sourceId);
        }

        private double cosineDistance(float[] left, float[] right) {
            double dot = 0;
            double leftNorm = 0;
            double rightNorm = 0;

            for (int index = 0; index < left.length; index++) {
                dot += left[index] * right[index];
                leftNorm += left[index] * left[index];
                rightNorm += right[index] * right[index];
            }

            return 1 - dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
        }

        @Override
        public boolean existsInFolder(Long userId, Long folderId) {
            return stored.values().stream().anyMatch(source -> source.getUserId().equals(userId)
                    && !source.isDeleted()
                    && source.getFolderId().equals(folderId));
        }

        @Override
        public List<KnowledgeSource> findPage(SourcePageQuery query) {
            Comparator<KnowledgeSource> recentFirst = Comparator
                    .comparing((KnowledgeSource source) -> source.getNode().getCreatedAt())
                    .thenComparing(KnowledgeSource::getId)
                    .reversed();

            return stored.values().stream()
                    .filter(source -> source.getUserId().equals(query.userId()))
                    .filter(source -> !source.isDeleted())
                    .filter(source -> source.getFolderId().equals(query.folderId()))
                    .filter(source -> query.status() == null
                            || source.getProcessingStatus() == query.status())
                    .sorted(recentFirst)
                    .filter(source -> afterCursor(source, query))
                    .limit(query.limit())
                    .map(Sources::copy)
                    .toList();
        }

        @Override
        public List<KnowledgeSource> findSearchPage(SourceSearchPageQuery query) {
            Comparator<KnowledgeSource> recentFirst = Comparator
                    .comparing((KnowledgeSource source) -> source.getNode().getCreatedAt())
                    .thenComparing(KnowledgeSource::getId)
                    .reversed();

            return stored.values().stream()
                    .filter(source -> source.getUserId().equals(query.userId()))
                    .filter(source -> !source.isDeleted())
                    .filter(source -> query.folderId() == null
                            || source.getFolderId().equals(query.folderId()))
                    .filter(source -> query.sourceIds() == null
                            || query.sourceIds().contains(source.getId()))
                    .sorted(recentFirst)
                    .filter(source -> afterCursor(source, query.cursorCreatedAt(), query.cursorNodeId()))
                    .limit(query.limit())
                    .map(Sources::copy)
                    .toList();
        }

        /** 커서보다 뒤에 오는 것만 남긴다. 정렬 기준과 같은 (생성 시각, id) 순서를 쓴다. */
        private boolean afterCursor(KnowledgeSource source, SourcePageQuery query) {
            return afterCursor(source, query.cursorCreatedAt(), query.cursorNodeId());
        }

        private boolean afterCursor(KnowledgeSource source, Instant cursorCreatedAt, UUID cursorNodeId) {
            if (cursorCreatedAt == null) {
                return true;
            }

            Instant createdAt = source.getNode().getCreatedAt();

            if (createdAt.isBefore(cursorCreatedAt)) {
                return true;
            }

            return createdAt.equals(cursorCreatedAt)
                    && source.getId().compareTo(cursorNodeId) < 0;
        }

        @Override
        public Optional<KnowledgeSource> findInFolderByCanonicalUrl(
                Long userId,
                Long folderId,
                String canonicalUrl
        ) {
            return stored.values().stream()
                    .filter(source -> source.getUserId().equals(userId)
                            && !source.isDeleted()
                            && Objects.equals(source.getFolderId(), folderId)
                            && Objects.equals(source.getCanonicalUrl(), canonicalUrl))
                    .findFirst()
                    .map(Sources::copy);
        }

        static KnowledgeSource copy(KnowledgeSource source) {
            return KnowledgeSource.restore(
                    Nodes.copy(source.getNode()),
                    source.getFolderId(),
                    source.getUrl(), source.getCanonicalUrl(), source.getContent(),
                    source.getSummary(), source.getSourceType(), source.getAuthor(),
                    source.getPublishedAt(), source.getProcessingStatus(), source.getAnalysisVersion(),
                    source.getReadAt()
            );
        }
    }

    public static class Branches implements KnowledgeBranchRepository {

        final Map<String, KnowledgeBranch> stored = new LinkedHashMap<>();

        private String key(UUID parent, UUID child) {
            return parent + ":" + child;
        }

        @Override
        public KnowledgeBranch save(KnowledgeBranch branch) {
            stored.put(key(branch.getParentNodeId(), branch.getChildNodeId()), copy(branch));
            return copy(branch);
        }

        @Override
        public Optional<KnowledgeBranch> find(UUID parentNodeId, UUID childNodeId) {
            return Optional.ofNullable(stored.get(key(parentNodeId, childNodeId))).map(Branches::copy);
        }

        @Override
        public List<KnowledgeBranch> findAllByParentNodeId(UUID parentNodeId) {
            return stored.values().stream()
                    .filter(branch -> branch.getParentNodeId().equals(parentNodeId))
                    .map(Branches::copy)
                    .collect(Collectors.toList());
        }

        @Override
        public List<KnowledgeBranch> findAllByChildNodeId(UUID childNodeId) {
            return stored.values().stream()
                    .filter(branch -> branch.getChildNodeId().equals(childNodeId))
                    .map(Branches::copy)
                    .collect(Collectors.toList());
        }

        @Override
        public void delete(UUID parentNodeId, UUID childNodeId) {
            stored.remove(key(parentNodeId, childNodeId));
        }

        static KnowledgeBranch copy(KnowledgeBranch branch) {
            return KnowledgeBranch.restore(
                    branch.getParentNodeId(), branch.getChildNodeId(),
                    branch.getPosition(), branch.getCreatedAt()
            );
        }
    }

    public static class Relations implements KnowledgeRelationRepository {

        final Map<String, KnowledgeRelation> stored = new LinkedHashMap<>();

        private String key(UUID from, UUID to, RelationType relationType) {
            return from + ":" + to + ":" + relationType;
        }

        @Override
        public KnowledgeRelation save(KnowledgeRelation relation) {
            String key = key(relation.getFromNodeId(), relation.getToNodeId(), relation.getRelationType());
            stored.put(key, copy(relation));
            return copy(relation);
        }

        @Override
        public Optional<KnowledgeRelation> find(UUID fromNodeId, UUID toNodeId, RelationType relationType) {
            return Optional.ofNullable(stored.get(key(fromNodeId, toNodeId, relationType)))
                    .map(Relations::copy);
        }

        @Override
        public List<KnowledgeRelation> findAllByFromNodeIdIn(
                Collection<UUID> fromNodeIds, Collection<RelationType> relationTypes
        ) {
            return stored.values().stream()
                    .filter(relation -> fromNodeIds.contains(relation.getFromNodeId())
                            && relationTypes.contains(relation.getRelationType()))
                    .map(Relations::copy)
                    .toList();
        }

        @Override
        public List<KnowledgeRelation> findAllByToNodeIdIn(
                Collection<UUID> toNodeIds, Collection<RelationType> relationTypes
        ) {
            return stored.values().stream()
                    .filter(relation -> toNodeIds.contains(relation.getToNodeId())
                            && relationTypes.contains(relation.getRelationType()))
                    .map(Relations::copy)
                    .toList();
        }

        @Override
        public void delete(UUID fromNodeId, UUID toNodeId, RelationType relationType) {
            stored.remove(key(fromNodeId, toNodeId, relationType));
        }

        static KnowledgeRelation copy(KnowledgeRelation relation) {
            return KnowledgeRelation.restore(
                    relation.getFromNodeId(), relation.getToNodeId(), relation.getRelationType(),
                    relation.getOrigin(), relation.getConfidence(), relation.getEvidence(),
                    relation.getCreatedAt()
            );
        }
    }
}
