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
        final Map<UUID, float[]> titleEmbeddings = new HashMap<>();
        final Map<UUID, String> titleEmbeddingModels = new HashMap<>();
        private Sources sources;
        private Relations relations;

        /** Category의 폴더 소속은 Source와 CONTAINS에서 파생한다. 그 조회를 쓰는 테스트만 잇는다. */
        public Nodes withFolderGraph(Sources sources, Relations relations) {
            this.sources = sources;
            this.relations = relations;
            return this;
        }

        @Override
        public List<KnowledgeNode> findCategoriesInFolder(Long userId, Long folderId) {
            if (sources == null || relations == null) {
                throw new IllegalStateException("withFolderGraph로 Source·관계 저장소를 이어야 한다");
            }
            Set<UUID> sourceIds = sources.stored.values().stream()
                    .filter(source -> !source.isDeleted()
                            && source.getUserId().equals(userId)
                            && folderId.equals(source.getFolderId()))
                    .map(KnowledgeSource::getId)
                    .collect(Collectors.toSet());
            Set<UUID> categoryIds = relations.stored.values().stream()
                    .filter(relation -> relation.getRelationType() == RelationType.CONTAINS
                            && sourceIds.contains(relation.getToNodeId()))
                    .map(KnowledgeRelation::getFromNodeId)
                    .collect(Collectors.toSet());
            return stored.values().stream()
                    .filter(node -> categoryIds.contains(node.getId())
                            && node.getUserId().equals(userId)
                            && node.getNodeType() == NodeType.CATEGORY
                            && !node.isDeleted())
                    .sorted(Comparator.comparing(KnowledgeNode::getId))
                    .map(Nodes::copy)
                    .toList();
        }

        @Override
        public KnowledgeNode create(KnowledgeNode node) {
            stored.put(node.getId(), copy(node));
            return copy(stored.get(node.getId()));
        }

        @Override
        public void delete(KnowledgeNode node) {
            stored.put(node.getId(), copy(node));
        }

        @Override
        public void updateTitle(KnowledgeNode node) {
            stored.put(node.getId(), copy(node));
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

        /** 정규화 제목 조회 왕복 횟수. 후보 수와 무관하게 1이어야 한다. */
        public int normalizedTitleLookupCount = 0;

        @Override
        public List<KnowledgeNode> findAllByNormalizedTitles(
                Long userId, NodeType nodeType, Collection<String> normalizedTitles
        ) {
            normalizedTitleLookupCount++;
            if (normalizedTitles.isEmpty()) {
                return List.of();
            }
            Set<String> wanted = Set.copyOf(normalizedTitles);
            return stored.values().stream()
                    .filter(node -> node.getUserId().equals(userId)
                            && node.getNodeType() == nodeType
                            && !node.isDeleted()
                            && wanted.contains(node.getNormalizedTitle()))
                    .map(Nodes::copy)
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
            KnowledgeNode saved = create(subject);
            titleEmbeddings.put(saved.getId(), titleEmbedding.clone());
            titleEmbeddingModels.put(saved.getId(), embeddingModel);
            return saved;
        }

        public void saveTitleEmbedding(
                Long userId,
                UUID subjectId,
                float[] titleEmbedding,
                String embeddingModel
        ) {
            KnowledgeNode subject = stored.get(subjectId);
            if (subject == null || subject.isDeleted()
                    || !subject.getUserId().equals(userId)
                    || subject.getNodeType() != NodeType.SUBJECT) {
                throw new IllegalStateException("failed to update subject title embedding: " + subjectId);
            }
            titleEmbeddings.put(subjectId, titleEmbedding.clone());
            titleEmbeddingModels.put(subjectId, embeddingModel);
        }

        @Override
        public int softDeleteAllOwnedByIds(Long userId, Collection<UUID> ids) {
            int updated = 0;
            for (UUID id : ids) {
                KnowledgeNode node = stored.get(id);
                if (node != null && node.getUserId().equals(userId) && !node.isDeleted()) {
                    node.delete();
                    stored.put(id, copy(node));
                    updated++;
                }
            }
            return updated;
        }

        public float[] titleEmbeddingOf(UUID subjectId) {
            float[] embedding = titleEmbeddings.get(subjectId);
            return embedding == null ? null : embedding.clone();
        }

        public String titleEmbeddingModelOf(UUID subjectId) {
            return titleEmbeddingModels.get(subjectId);
        }

        @Override
        public void deleteById(UUID id) {
            stored.remove(id);
            titleEmbeddings.remove(id);
            titleEmbeddingModels.remove(id);
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
                    node.getUpdatedAt() == null ? createdAt : node.getUpdatedAt(),
                    node.getTitleRenamedAt()
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
        public List<KnowledgeSource> findAllActiveByIds(Long userId, Collection<UUID> nodeIds) {
            return findAllByIds(nodeIds).stream()
                    .filter(source -> source.getUserId().equals(userId))
                    .toList();
        }

        @Override
        public List<KnowledgeSource> findAllActiveInFolderByIds(
                Long userId,
                Long folderId,
                Collection<UUID> nodeIds
        ) {
            return findAllActiveByIds(userId, nodeIds).stream()
                    .filter(source -> folderId.equals(source.getFolderId()))
                    .toList();
        }

        @Override
        public List<UUID> findAliveNodeIdsInFolder(Long userId, Long folderId) {
            return stored.values().stream()
                    .filter(source -> source.getUserId().equals(userId))
                    .filter(source -> !source.isDeleted())
                    .filter(source -> folderId.equals(source.getFolderId()))
                    .map(KnowledgeSource::getId)
                    .toList();
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
            return findAllActiveInFolderByIds(userId, folderId, nodeIds).stream()
                    .filter(source -> source.getProcessingStatus() == SourceProcessingStatus.COMPLETED)
                    .filter(source -> source.getSummary() != null)
                    .sorted(Comparator
                            .comparing((KnowledgeSource source) -> source.getNode().getCreatedAt(),
                                    Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(KnowledgeSource::getId))
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
        public void updateStatus(
                UUID sourceId,
                SourceProcessingStatus status,
                String failureMessage,
                boolean retryable
        ) {
            KnowledgeSource source = stored.get(sourceId);
            if (source == null) {
                return;
            }
            if (status == SourceProcessingStatus.FAILED) {
                source.failDigestion(failureMessage, retryable);
            } else {
                source.updateStatus(status);
            }
            stored.put(sourceId, copy(source));
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
        public List<KnowledgeSource> findAllInFolderByCanonicalUrls(
                Long userId,
                Long folderId,
                Collection<String> canonicalUrls
        ) {
            return stored.values().stream()
                    .filter(source -> source.getUserId().equals(userId)
                            && !source.isDeleted()
                            && Objects.equals(source.getFolderId(), folderId)
                            && canonicalUrls.contains(source.getCanonicalUrl()))
                    .map(Sources::copy)
                    .toList();
        }

        static KnowledgeSource copy(KnowledgeSource source) {
            return KnowledgeSource.restore(
                    Nodes.copy(source.getNode()),
                    source.getFolderId(),
                    source.getUrl(), source.getCanonicalUrl(), source.getContent(),
                    source.getSummary(), source.getSourceType(), source.getAuthor(),
                    source.getPublishedAt(), source.getProcessingStatus(), source.getAnalysisVersion(),
                    source.getFailureMessage(), source.isRetryable(),
                    source.getReadAt()
            );
        }
    }

    public static class VectorSearch implements KnowledgeVectorSearchRepository {

        private final Nodes nodes;
        private final Sources sources;

        public VectorSearch(Nodes nodes, Sources sources) {
            this.nodes = nodes;
            this.sources = sources;
        }

        @Override
        public List<KnowledgeNode> findSimilarSubjects(
                Long userId,
                float[] titleEmbedding,
                String embeddingModel,
                int limit
        ) {
            return nodes.stored.values().stream()
                    .filter(node -> node.getUserId().equals(userId)
                            && node.getNodeType() == NodeType.SUBJECT
                            && !node.isDeleted()
                            && Objects.equals(nodes.titleEmbeddingModels.get(node.getId()), embeddingModel)
                            && nodes.titleEmbeddings.containsKey(node.getId()))
                    .sorted(Comparator.comparingDouble(node -> cosineDistance(
                            titleEmbedding, nodes.titleEmbeddings.get(node.getId()))))
                    .limit(limit)
                    .map(Nodes::copy)
                    .toList();
        }

        @Override
        public List<SimilarSource> findSimilarSources(
                Long userId,
                UUID excludedSourceId,
                float[] summaryEmbedding,
                String embeddingModel,
                int limit
        ) {
            return sources.stored.values().stream()
                    .filter(source -> source.getUserId().equals(userId))
                    .filter(source -> !source.getId().equals(excludedSourceId))
                    .filter(source -> !source.isDeleted())
                    .filter(source -> source.getProcessingStatus() == SourceProcessingStatus.COMPLETED)
                    .filter(source -> sources.summaryEmbeddings.containsKey(source.getId()))
                    .filter(source -> Objects.equals(
                            sources.summaryEmbeddingModels.get(source.getId()), embeddingModel))
                    .sorted(Comparator.comparingDouble(source -> cosineDistance(
                            sources.summaryEmbeddings.get(source.getId()), summaryEmbedding)))
                    .limit(limit)
                    .map(source -> new SimilarSource(
                            source.getId(), source.getNode().getTitle(),
                            cosineDistance(sources.summaryEmbeddings.get(source.getId()), summaryEmbedding)))
                    .toList();
        }

        private static double cosineDistance(float[] left, float[] right) {
            double dot = 0;
            double leftNorm = 0;
            double rightNorm = 0;
            for (int index = 0; index < left.length; index++) {
                dot += left[index] * right[index];
                leftNorm += left[index] * left[index];
                rightNorm += right[index] * right[index];
            }
            if (leftNorm == 0 || rightNorm == 0) {
                return 1;
            }
            return 1 - dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
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

        /** (fromNodeId, relationType) 묶음당 조회 한 번. 실제 저장소의 왕복 횟수를 센다. */
        public int lookupCount = 0;

        @Override
        public List<KnowledgeRelation> saveAll(List<KnowledgeRelation> relations) {
            if (relations.isEmpty()) {
                return List.of();
            }

            Map<String, KnowledgeRelation> requested = new LinkedHashMap<>();
            relations.forEach(relation -> requested.put(
                    key(relation.getFromNodeId(), relation.getToNodeId(), relation.getRelationType()),
                    relation
            ));

            lookupCount += (int) requested.values().stream()
                    .map(relation -> relation.getFromNodeId() + ":" + relation.getRelationType())
                    .distinct()
                    .count();

            return requested.entrySet().stream()
                    .map(entry -> {
                        KnowledgeRelation existing = stored.get(entry.getKey());
                        KnowledgeRelation merged = merge(existing, entry.getValue());
                        stored.put(entry.getKey(), merged);
                        return copy(merged);
                    })
                    .toList();
        }

        /** 사용자가 만든 관계는 AI 재관찰로 덮어쓰지 않는다. */
        private static KnowledgeRelation merge(KnowledgeRelation existing, KnowledgeRelation observed) {
            if (existing == null) {
                return copy(observed);
            }

            KnowledgeRelation merged = copy(existing);
            merged.applyObservation(
                    observed.getOrigin(), observed.getConfidence(), observed.getEvidence()
            );
            return merged;
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

        @Override
        public int deleteAllFrom(UUID fromNodeId, RelationType relationType) {
            List<String> keys = stored.entrySet().stream()
                    .filter(entry -> entry.getValue().getFromNodeId().equals(fromNodeId)
                            && entry.getValue().getRelationType() == relationType)
                    .map(Map.Entry::getKey)
                    .toList();
            keys.forEach(stored::remove);
            return keys.size();
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
