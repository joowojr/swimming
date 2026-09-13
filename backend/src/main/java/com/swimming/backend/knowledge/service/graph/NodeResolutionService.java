package com.swimming.backend.knowledge.service.graph;

import com.swimming.backend.knowledge.config.ResolutionProperties;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeRelation;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.NodeTitleNormalizer;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.RelationType;
import com.swimming.backend.knowledge.dto.out.ResolvedNode;
import com.swimming.backend.knowledge.repository.SimilarSource;
import com.swimming.backend.knowledge.service.data.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.data.KnowledgeRelationService;
import com.swimming.backend.knowledge.service.data.KnowledgeSourceService;
import com.swimming.backend.knowledge.service.data.KnowledgeVectorSearchService;
import com.swimming.backend.common.client.EmbeddingClient;
import com.swimming.backend.knowledge.service.llm.NodeResolutionLlmDecision;
import com.swimming.backend.knowledge.service.llm.NodeResolutionLlmRequest;
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
            for (ResolvedNode item : resolveSemantically(
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

    /** 후보별 임베딩 검색 결과와 유사 Source 문맥을 사용해 의미가 같은 Subject를 찾는다. */
    private List<ResolvedNode> resolveSemantically(
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
        Map<Integer, List<KnowledgeNode>> subjectsByCandidate = searchResults.byCandidate();
        Map<UUID, KnowledgeNode> reusableSubjectsById = new LinkedHashMap<>();
        List<NodeResolutionLlmRequest.Candidate> candidates = new ArrayList<>();

        for (int candidateIndex = 0; candidateIndex < unresolved.size(); candidateIndex++) {
            List<KnowledgeNode> matches = subjectsByCandidate.getOrDefault(candidateIndex, List.of());
            matches.forEach(match -> reusableSubjectsById.putIfAbsent(match.getId(), match));
            Candidate candidate = unresolved.get(candidateIndex);
            candidates.add(new NodeResolutionLlmRequest.Candidate(
                    candidate.normalized(),
                    candidate.value(),
                    matches.stream().map(NodeResolutionService::toReusableSubject).toList()
            ));
        }

        contextSubjects.forEach(subject ->
                reusableSubjectsById.putIfAbsent(subject.getId(), subject));

        List<NodeResolutionLlmDecision> decisions = resolutionLlmService.resolve(
                new NodeResolutionLlmRequest(
                        summary,
                        candidates,
                        contextSubjects.stream()
                                .map(NodeResolutionService::toReusableSubject)
                                .toList()
                ));
        List<ResolvedNode> resolved = applyLlmDecisions(
                source.getUserId(), unresolved, reusableSubjectsById, decisions);
        log.info(
                "[node-resolution-v2] sourceId={} finished resolvedSubjects={}",
                source.getId(),
                resolved.stream().map(item -> item.node().getTitle()).toList()
        );
        return resolved;
    }

    /** LLM 판정을 원래 후보 순서대로 기존 Subject 재사용 또는 신규 생성에 반영한다. */
    private List<ResolvedNode> applyLlmDecisions(
            Long userId,
            List<Candidate> unresolved,
            Map<UUID, KnowledgeNode> reusableSubjectsById,
            List<NodeResolutionLlmDecision> llmDecisions
    ) {
        if (llmDecisions == null) {
            throw new IllegalStateException("node resolution v2 result is empty");
        }

        Map<String, NodeResolutionLlmDecision> decisionByCandidateKey =
                indexDecisionsByCandidateKey(llmDecisions);
        SubjectCreationContext creationContext = prepareSubjectCreation(
                userId, decisionByCandidateKey.values());

        List<ResolvedNode> resolved = new ArrayList<>();
        for (Candidate candidate : unresolved) {
            NodeResolutionLlmDecision decision = decisionByCandidateKey.get(
                    candidate.normalized());
            ResolvedNode resolvedNode = applyDecisionToCandidate(
                    userId,
                    candidate,
                    decision,
                    reusableSubjectsById,
                    creationContext
            );
            if (resolvedNode != null) {
                resolved.add(resolvedNode);
            }
        }
        if (resolved.isEmpty()) {
            throw new IllegalStateException("node resolution v2 returned no usable decision");
        }
        return resolved;
    }

    private Map<String, NodeResolutionLlmDecision> indexDecisionsByCandidateKey(
            List<NodeResolutionLlmDecision> decisions
    ) {
        return decisions.stream()
                .collect(java.util.stream.Collectors.toMap(
                        NodeResolutionLlmDecision::candidateKey,
                        decision -> decision,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
    }

    /** LLM 호출 중 생성된 Subject를 재확인하고, 실제 신규 제목의 embedding만 계산한다. */
    private SubjectCreationContext prepareSubjectCreation(
            Long userId,
            java.util.Collection<NodeResolutionLlmDecision> decisions
    ) {
        Map<String, KnowledgeNode> createdMeanwhile = nodeService.findSubjectsByNormalizedTitles(
                userId,
                decisions.stream()
                        .filter(decision -> !decision.reusesSubject())
                        .map(NodeResolutionLlmDecision::newSubjectTitle)
                        .map(NodeTitleNormalizer::normalize)
                        .filter(normalized -> !normalized.isEmpty())
                        .distinct()
                        .toList()
        );
        Map<String, float[]> embeddingsByNormalizedTitle = embedNewSubjectTitles(
                decisions, createdMeanwhile);
        return new SubjectCreationContext(
                new LinkedHashMap<>(createdMeanwhile),
                embeddingsByNormalizedTitle
        );
    }

    private ResolvedNode applyDecisionToCandidate(
            Long userId,
            Candidate candidate,
            NodeResolutionLlmDecision decision,
            Map<UUID, KnowledgeNode> reusableSubjectsById,
            SubjectCreationContext creationContext
    ) {
        if (decision == null) {
            return null;
        }

        if (decision.reusesSubject()) {
            return reuseSubject(candidate, decision, reusableSubjectsById);
        }
        return createOrReuseSubject(userId, candidate, decision, creationContext);
    }

    private ResolvedNode reuseSubject(
            Candidate candidate,
            NodeResolutionLlmDecision decision,
            Map<UUID, KnowledgeNode> reusableSubjectsById
    ) {
        KnowledgeNode matched = reusableSubjectsById.get(decision.reusedSubjectId());
        return matched == null ? null : ResolvedNode.semantic(candidate.value(), matched);
    }

    private ResolvedNode createOrReuseSubject(
            Long userId,
            Candidate candidate,
            NodeResolutionLlmDecision decision,
            SubjectCreationContext creationContext
    ) {
        String title = decision.newSubjectTitle();
        String normalizedTitle = NodeTitleNormalizer.normalize(title);
        if (normalizedTitle.isEmpty()) {
            return null;
        }

        KnowledgeNode existing = creationContext.subjectsByNormalizedTitle().get(normalizedTitle);
        if (existing != null) {
            return ResolvedNode.exact(candidate.value(), existing);
        }

        float[] titleEmbedding = creationContext.embeddingsByNormalizedTitle().get(normalizedTitle);
        if (titleEmbedding == null) {
            return null;
        }

        ResolvedNode created = createSubject(
                userId, candidate.value(), title, titleEmbedding);
        creationContext.subjectsByNormalizedTitle().put(normalizedTitle, created.node());
        return created;
    }

    /** 실제로 새로 만들 Subject 제목만 한 번에 임베딩한다. */
    private Map<String, float[]> embedNewSubjectTitles(
            java.util.Collection<NodeResolutionLlmDecision> decisions,
            Map<String, KnowledgeNode> existingSubjects
    ) {
        Map<String, String> titlesByNormalizedTitle = new LinkedHashMap<>();
        for (NodeResolutionLlmDecision decision : decisions) {
            if (decision.reusesSubject()) {
                continue;
            }
            String title = decision.newSubjectTitle();
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

    private static NodeResolutionLlmRequest.ReusableSubject toReusableSubject(
            KnowledgeNode subject
    ) {
        return new NodeResolutionLlmRequest.ReusableSubject(
                subject.getId(), subject.getTitle());
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

    private record SubjectCreationContext(
            Map<String, KnowledgeNode> subjectsByNormalizedTitle,
            Map<String, float[]> embeddingsByNormalizedTitle
    ) {
    }

}
