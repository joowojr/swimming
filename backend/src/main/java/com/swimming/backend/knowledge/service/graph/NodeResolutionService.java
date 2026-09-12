package com.swimming.backend.knowledge.service.graph;

import com.swimming.backend.knowledge.config.ResolutionProperties;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeRelation;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.NodeTitleNormalizer;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.RelationType;
import com.swimming.backend.knowledge.dto.out.NodeResolutionInputV2;
import com.swimming.backend.knowledge.dto.out.NodeResolutionResultV2;
import com.swimming.backend.knowledge.dto.out.ResolvedNode;
import com.swimming.backend.knowledge.repository.SimilarSource;
import com.swimming.backend.knowledge.service.data.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.data.KnowledgeRelationService;
import com.swimming.backend.knowledge.service.data.KnowledgeSourceService;
import com.swimming.backend.knowledge.service.data.KnowledgeVectorSearchService;
import com.swimming.backend.common.client.EmbeddingClient;
import com.swimming.backend.knowledge.service.llm.NodeResolutionLlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Subject 후보를 실제 노드로 확정한다.
 *
 * <p>표기 규칙으로 명확한 중복을 먼저 제거한다. 남은 후보가 있을 때 현재 Source와 유사한
 * Source의 Subject 조회 및 Subject 직접 임베딩 검색을 병렬로 수행한다. 두 결과의 합집합 안에서
 * LLM이 재사용 대상을 고르게 한다. 네트워크 호출을 포함하므로 이 서비스 전체를 트랜잭션으로
 * 묶지 않는다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NodeResolutionService {

    static final String EMBEDDING_MODEL = "text-embedding-3-small";
    static final int EMBEDDING_DIMENSIONS = 768;

    private final EmbeddingClient embeddingClient;
    private final ResolutionProperties properties;
    private final KnowledgeSourceService sourceService;
    private final KnowledgeNodeService nodeService;
    private final KnowledgeVectorSearchService vectorSearchService;
    private final KnowledgeRelationService relationService;
    private final NodeResolutionLlmService resolutionLlmService;

    /** Subject 후보를 규칙 기반과 의미 기반 순서로 해석한다. */
    public List<ResolvedNode> resolveSubjects(
            KnowledgeSource source,
            String summary,
            List<String> rawCandidates
    ) {
        float[] summaryEmbedding = embedOne(summary, "source summary");
        sourceService.saveSummaryEmbedding(
                source.getUserId(), source.getId(), summaryEmbedding, EMBEDDING_MODEL
        );

        List<Candidate> candidates = normalizeCandidates(rawCandidates);
        if (candidates.isEmpty()) {
            log.info("[node-resolution] sourceId={} has no candidate to resolve", source.getId());
            return List.of();
        }

        Map<String, ResolvedNode> resolved = new LinkedHashMap<>();
        List<Candidate> unresolved = new ArrayList<>();

        Map<String, KnowledgeNode> exactMatches = exactMatchesInFolder(
                source.getUserId(),
                source.getFolderId(),
                candidates.stream().map(Candidate::normalized).toList()
        );
        for (Candidate candidate : candidates) {
            KnowledgeNode node = exactMatches.get(candidate.normalized());
            if (node == null) {
                unresolved.add(candidate);
                continue;
            }
            resolved.put(candidate.normalized(), ResolvedNode.exact(candidate.value(), node));
        }

        log.info(
                "[node-resolution] sourceId={} candidates={} matchedByTitle={} unresolved={}",
                source.getId(),
                candidates.stream().map(Candidate::value).toList(),
                resolved.values().stream().map(item -> item.node().getTitle()).toList(),
                unresolved.stream().map(Candidate::value).toList()
        );

        if (!unresolved.isEmpty()) {
            for (ResolvedNode item : resolveSemanticallyV2(
                    source, summary, summaryEmbedding, unresolved
            )) {
                resolved.put(NodeTitleNormalizer.normalize(item.candidate()), item);
            }
        }

        // 어긋난 결정 때문에 버려진 후보는 결과에 자리가 없다.
        return deduplicateNodes(candidates.stream()
                .map(candidate -> resolved.get(candidate.normalized()))
                .filter(Objects::nonNull)
                .toList());
    }

    /** 현재 폴더의 활성 Source가 ABOUT 관계로 사용 중인 Subject만 정확 일치로 인정한다. */
    private Map<String, KnowledgeNode> exactMatchesInFolder(
            Long userId,
            Long folderId,
            List<String> normalizedTitles
    ) {
        Map<String, KnowledgeNode> matches = nodeService.findSubjectsByNormalizedTitles(
                userId, normalizedTitles);
        if (matches.isEmpty()) {
            return Map.of();
        }

        List<KnowledgeRelation> relations = relationService.findIncoming(
                matches.values().stream().map(KnowledgeNode::getId).toList(),
                List.of(RelationType.ABOUT)
        );
        Set<UUID> sourceIdsInFolder = sourceService.findAllByIds(
                        relations.stream().map(KnowledgeRelation::getFromNodeId).distinct().toList()
                ).stream()
                .filter(item -> item.getFolderId().equals(folderId))
                .map(KnowledgeSource::getId)
                .collect(java.util.stream.Collectors.toSet());
        Set<UUID> subjectIdsInFolder = relations.stream()
                .filter(relation -> sourceIdsInFolder.contains(relation.getFromNodeId()))
                .map(KnowledgeRelation::getToNodeId)
                .collect(java.util.stream.Collectors.toSet());

        return matches.entrySet().stream()
                .filter(entry -> subjectIdsInFolder.contains(entry.getValue().getId()))
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    /** 텍스트를 임베딩하고 저장 규격인 768차원인지 검증한다. */
    private float[] embedOne(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(label + " is empty");
        }

        float[] embedding = embeddingClient.embed(value);
        if (embedding == null || embedding.length != EMBEDDING_DIMENSIONS) {
            throw new IllegalStateException(
                    "expected %d embedding dimensions but got %s".formatted(
                            EMBEDDING_DIMENSIONS,
                            embedding == null ? "null" : embedding.length
                    )
            );
        }
        return embedding;
    }

    /** 후보 값을 정규화하고 같은 normalized title을 가진 중복 후보를 제거한다. */
    private List<Candidate> normalizeCandidates(List<String> rawCandidates) {
        Map<String, Candidate> unique = new LinkedHashMap<>();

        if (rawCandidates == null) {
            return List.of();
        }

        for (String rawCandidate : rawCandidates) {
            String normalized = NodeTitleNormalizer.normalize(rawCandidate);
            if (!normalized.isEmpty()) {
                unique.putIfAbsent(normalized, new Candidate(rawCandidate.strip(), normalized));
            }
        }

        return List.copyOf(unique.values());
    }

    /** v2: 후보별 임베딩 검색 결과를 후보 내부 매칭으로 보존한다. */
    private List<ResolvedNode> resolveSemanticallyV2(
            KnowledgeSource source,
            String summary,
            float[] summaryEmbedding,
            List<Candidate> unresolved
    ) {
        CompletableFuture<CandidateSearchResults> subjectSearchFuture = CompletableFuture.supplyAsync(
                () -> subjectsFromEmbedding(source.getUserId(), unresolved));
        CompletableFuture<List<KnowledgeNode>> sourceSearchFuture = CompletableFuture.supplyAsync(
                () -> subjectsFromSimilarSources(source, summaryEmbedding));
        CandidateSearchResults searchResults = subjectSearchFuture.join();
        List<KnowledgeNode> contextSubjects = sourceSearchFuture.join();
        List<NodeResolutionInputV2.Candidate> candidates = new ArrayList<>();
        Map<Integer, List<KnowledgeNode>> subjectsByCandidate = searchResults.byCandidate();
        Map<Integer, KnowledgeNode> reusableSubjectsByIndex = new LinkedHashMap<>();
        Map<UUID, Integer> reuseIndexBySubjectId = new LinkedHashMap<>();
        int nextReuseIndex = 1;

        for (int candidateIndex = 0; candidateIndex < unresolved.size(); candidateIndex++) {
            List<KnowledgeNode> matches = subjectsByCandidate.getOrDefault(candidateIndex, List.of());
            List<NodeResolutionInputV2.Match> indexedMatches = new ArrayList<>();
            for (KnowledgeNode match : matches) {
                Integer reuseIndex = reuseIndexBySubjectId.get(match.getId());
                if (reuseIndex == null) {
                    reuseIndex = nextReuseIndex++;
                    reuseIndexBySubjectId.put(match.getId(), reuseIndex);
                    reusableSubjectsByIndex.put(reuseIndex, match);
                }
                indexedMatches.add(new NodeResolutionInputV2.Match(
                        reuseIndex, match.getTitle()));
            }
            candidates.add(new NodeResolutionInputV2.Candidate(
                    candidateIndex + 1,
                    unresolved.get(candidateIndex).value(),
                    indexedMatches
            ));
        }

        List<NodeResolutionInputV2.ContextSubject> indexedContextSubjects = new ArrayList<>();
        for (KnowledgeNode contextSubject : contextSubjects) {
            if (reuseIndexBySubjectId.containsKey(contextSubject.getId())) {
                continue;
            }
            int reuseIndex = nextReuseIndex++;
            reuseIndexBySubjectId.put(contextSubject.getId(), reuseIndex);
            reusableSubjectsByIndex.put(reuseIndex, contextSubject);
            indexedContextSubjects.add(new NodeResolutionInputV2.ContextSubject(
                    reuseIndex, contextSubject.getTitle()));
        }

        NodeResolutionResultV2 result = resolutionLlmService.resolveV2(
                new NodeResolutionInputV2(
                        summary,
                        candidates,
                        indexedContextSubjects
                ));
        List<ResolvedNode> resolved = materializeV2(
                source.getUserId(), unresolved, reusableSubjectsByIndex, result);
        log.info(
                "[node-resolution-v2] sourceId={} finished resolvedSubjects={}",
                source.getId(),
                resolved.stream().map(item -> item.node().getTitle()).toList()
        );
        return resolved;
    }

    private List<ResolvedNode> materializeV2(
            Long userId,
            List<Candidate> unresolved,
            Map<Integer, KnowledgeNode> reusableSubjectsByIndex,
            NodeResolutionResultV2 result
    ) {
        if (result == null || result.decisions() == null) {
            throw new IllegalStateException("node resolution v2 result is empty");
        }

        Map<Integer, NodeResolutionResultV2.Decision> decisions = new LinkedHashMap<>();
        for (NodeResolutionResultV2.Decision decision : result.decisions()) {
            if (decision == null || decision.reuseIndex() < 0
                    || decision.candidateIndex() < 1
                    || decision.candidateIndex() > unresolved.size()) {
                continue;
            }
            decisions.putIfAbsent(decision.candidateIndex(), decision);
        }

        Map<String, KnowledgeNode> createdMeanwhile = nodeService.findSubjectsByNormalizedTitles(
                userId,
                decisions.values().stream()
                        .filter(decision -> decision.reuseIndex() == 0)
                        .map(NodeResolutionService::newSubjectTitleV2)
                        .map(NodeTitleNormalizer::normalize)
                        .filter(normalized -> !normalized.isEmpty())
                        .distinct()
                        .toList()
        );
        Map<String, float[]> embeddingsByNormalizedTitle = embedNewSubjectTitles(
                decisions.values(), createdMeanwhile);
        Map<String, KnowledgeNode> availableCreatedSubjects = new LinkedHashMap<>(createdMeanwhile);

        List<ResolvedNode> resolved = new ArrayList<>();
        for (int index = 0; index < unresolved.size(); index++) {
            NodeResolutionResultV2.Decision decision = decisions.get(index + 1);
            if (decision == null) {
                continue;
            }
            Candidate candidate = unresolved.get(index);
            if (decision.reuseIndex() > 0) {
                KnowledgeNode matched = reusableSubjectsByIndex.get(decision.reuseIndex());
                if (matched == null) {
                    continue;
                }
                resolved.add(ResolvedNode.semantic(
                        candidate.value(), matched));
            } else if (decision.reuseIndex() == 0) {
                String title = newSubjectTitleV2(decision);
                String normalizedTitle = NodeTitleNormalizer.normalize(title);
                if (!normalizedTitle.isEmpty()) {
                    KnowledgeNode existing = availableCreatedSubjects.get(normalizedTitle);
                    if (existing != null) {
                        resolved.add(ResolvedNode.exact(candidate.value(), existing));
                        continue;
                    }
                    float[] titleEmbedding = embeddingsByNormalizedTitle.get(normalizedTitle);
                    if (titleEmbedding == null) {
                        continue;
                    }
                    ResolvedNode created = createSubject(
                            userId, candidate.value(), title, titleEmbedding);
                    availableCreatedSubjects.put(normalizedTitle, created.node());
                    resolved.add(created);
                }
            }
        }
        if (resolved.isEmpty()) {
            throw new IllegalStateException("node resolution v2 returned no usable decision");
        }
        return resolved;
    }

    private static String newSubjectTitleV2(NodeResolutionResultV2.Decision decision) {
        return decision.value() == null ? "" : decision.value().strip();
    }

    /** 실제로 새로 만들 Subject 제목만 한 번에 임베딩한다. */
    private Map<String, float[]> embedNewSubjectTitles(
            java.util.Collection<NodeResolutionResultV2.Decision> decisions,
            Map<String, KnowledgeNode> existingSubjects
    ) {
        Map<String, String> titlesByNormalizedTitle = new LinkedHashMap<>();
        for (NodeResolutionResultV2.Decision decision : decisions) {
            if (decision.reuseIndex() != 0) {
                continue;
            }
            String title = newSubjectTitleV2(decision);
            String normalizedTitle = NodeTitleNormalizer.normalize(title);
            if (!normalizedTitle.isEmpty() && !existingSubjects.containsKey(normalizedTitle)) {
                titlesByNormalizedTitle.putIfAbsent(normalizedTitle, title);
            }
        }
        if (titlesByNormalizedTitle.isEmpty()) {
            return Map.of();
        }

        List<float[]> embeddings = embeddingClient.embed(titlesByNormalizedTitle.values().stream()
                .map(this::embeddingText)
                .toList());
        validateEmbeddings(embeddings, titlesByNormalizedTitle.size());

        Map<String, float[]> embeddingsByNormalizedTitle = new LinkedHashMap<>();
        int index = 0;
        for (String normalizedTitle : titlesByNormalizedTitle.keySet()) {
            embeddingsByNormalizedTitle.put(normalizedTitle, embeddings.get(index++));
        }
        return embeddingsByNormalizedTitle;
    }

    private List<KnowledgeNode> subjectsFromSimilarSources(
            KnowledgeSource source,
            float[] summaryEmbedding
    ) {
        List<SimilarSource> similarSources = vectorSearchService.findSimilarSources(
                source.getUserId(),
                source.getId(),
                summaryEmbedding,
                EMBEDDING_MODEL,
                properties.similarSourceLimit()
        );
        List<SimilarSource> selectedSources = similarSources.stream()
                .filter(item -> item.distance() <= properties.similarSourceDistanceThreshold())
                .toList();
        List<UUID> similarSourceIds = selectedSources.stream()
                .map(SimilarSource::sourceId)
                .toList();
        List<KnowledgeNode> subjects = List.copyOf(
                subjectsOf(source.getUserId(), similarSourceIds).values());
        log.info(
                "[node-resolution] sourceId={} similarSources={} distanceThreshold={} selectedSources={} subjectsFromSources={}",
                source.getId(),
                similarSources.stream()
                        .map(item -> "'%s'(%s, distance=%f)"
                                .formatted(item.title(), item.sourceId(), item.distance()))
                        .toList(),
                properties.similarSourceDistanceThreshold(),
                selectedSources.stream()
                        .map(item -> "'%s'(%s, distance=%f)"
                                .formatted(item.title(), item.sourceId(), item.distance()))
                        .toList(),
                titlesOf(subjects)
        );
        return subjects;
    }

    /** 미해결 후보만 임베딩하고 DB에 저장된 Subject embedding 상위 K개를 수집한다. */
    private CandidateSearchResults subjectsFromEmbedding(
            Long userId,
            List<Candidate> unresolved
    ) {
        List<String> inputs = unresolved.stream()
                .map(Candidate::value)
                .map(this::embeddingText)
                .toList();
        List<float[]> embeddings = embeddingClient.embed(inputs);
        validateEmbeddings(embeddings, inputs.size());

        Map<Integer, List<KnowledgeNode>> byCandidate = new LinkedHashMap<>();
        for (int candidateIndex = 0; candidateIndex < unresolved.size(); candidateIndex++) {
            List<KnowledgeNode> similar = vectorSearchService.findSimilarSubjects(
                    userId,
                    embeddings.get(candidateIndex),
                    EMBEDDING_MODEL,
                    properties.subjectTopK()
            );
            log.info(
                    "[node-resolution] candidate='{}' subjectsByTitleEmbedding(top{})={}",
                    unresolved.get(candidateIndex).value(), properties.subjectTopK(),
                    titlesOf(similar)
            );
            byCandidate.put(candidateIndex, List.copyOf(similar));
        }
        return new CandidateSearchResults(byCandidate);
    }

    private static List<String> titlesOf(List<KnowledgeNode> nodes) {
        return nodes.stream().map(KnowledgeNode::getTitle).toList();
    }

    private String embeddingText(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private void validateEmbeddings(List<float[]> embeddings, int expectedCount) {
        if (embeddings == null || embeddings.size() != expectedCount
                || embeddings.stream().anyMatch(embedding ->
                embedding == null || embedding.length != EMBEDDING_DIMENSIONS)) {
            throw new IllegalStateException(
                    "expected %d embeddings with %d dimensions".formatted(
                            expectedCount, EMBEDDING_DIMENSIONS));
        }
    }

    /** 유사 Source가 ABOUT 관계로 가리키는 현재 사용자의 Subject를 수집한다. */
    private Map<UUID, KnowledgeNode> subjectsOf(Long userId, List<UUID> sourceIds) {
        if (sourceIds.isEmpty()) {
            return Map.of();
        }

        List<UUID> subjectIds = relationService.findOutgoing(
                        sourceIds, List.of(RelationType.ABOUT)
                ).stream()
                .map(KnowledgeRelation::getToNodeId)
                .distinct()
                .toList();

        Map<UUID, KnowledgeNode> subjects = new LinkedHashMap<>();
        for (KnowledgeNode node : nodeService.findAllByIds(subjectIds)) {
            if (node.getUserId().equals(userId) && node.getNodeType() == NodeType.SUBJECT) {
                subjects.putIfAbsent(node.getId(), node);
            }
        }
        return subjects;
    }

    /** 미리 계산한 Subject title embedding과 새 노드를 함께 저장한다. */
    private ResolvedNode createSubject(
            Long userId,
            String candidate,
            String title,
            float[] titleEmbedding
    ) {
        KnowledgeNode created = nodeService.createSubjectWithEmbedding(
                userId, title, null, titleEmbedding, EMBEDDING_MODEL
        );
        return ResolvedNode.created(candidate, created);
    }

    /** 여러 후보가 같은 Subject로 확정된 경우 최초 결과 하나만 남긴다. */
    private List<ResolvedNode> deduplicateNodes(List<ResolvedNode> resolved) {
        Set<UUID> seen = new LinkedHashSet<>();
        return resolved.stream().filter(item -> seen.add(item.node().getId())).toList();
    }

    private record Candidate(String value, String normalized) {
    }

    private record CandidateSearchResults(Map<Integer, List<KnowledgeNode>> byCandidate) {
    }

}
