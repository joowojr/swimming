package com.swimming.backend.knowledge.service.graph;

import com.swimming.backend.knowledge.config.ResolutionProperties;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeRelation;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.NodeTitleNormalizer;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.RelationType;
import com.swimming.backend.knowledge.dto.out.NodeResolutionInput;
import com.swimming.backend.knowledge.dto.out.NodeResolutionResult;
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
import java.util.stream.IntStream;

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

    /** Source 검색과 Subject 직접 검색을 병렬 실행하고 합집합을 Context로 제공한다. */
    private List<ResolvedNode> resolveSemantically(
            KnowledgeSource source,
            String summary,
            float[] summaryEmbedding,
            List<Candidate> unresolved
    ) {
        CompletableFuture<List<KnowledgeNode>> sourceSearchFuture = CompletableFuture.supplyAsync(
                () -> subjectsFromSimilarSources(source, summaryEmbedding));
        CompletableFuture<CandidateSearchResults> subjectSearchFuture = CompletableFuture.supplyAsync(
                () -> subjectsFromEmbedding(source.getUserId(), unresolved));
        CompletableFuture.allOf(sourceSearchFuture, subjectSearchFuture).join();

        CandidateSearchResults candidateSearchResults = subjectSearchFuture.join();
        List<KnowledgeNode> reusableSubjects = unionSubjects(
                candidateSearchResults.subjects(), sourceSearchFuture.join());

        // 프롬프트의 reusable-subjects가 R1부터 이 순서로 나간다.
        if (reusableSubjects.isEmpty()) {
            log.info("[node-resolution] sourceId={} has no reusable subject", source.getId());
        } else {
            log.info(
                    "[node-resolution] sourceId={} reusableSubjects(R1..R{})={}",
                    source.getId(), reusableSubjects.size(), titlesOf(reusableSubjects)
            );
        }

        NodeResolutionInput input = new NodeResolutionInput(
                summary,
                IntStream.range(0, unresolved.size())
                        .mapToObj(index -> new NodeResolutionInput.Candidate(
                                index + 1, unresolved.get(index).value()
                        ))
                        .toList(),
                IntStream.range(0, reusableSubjects.size())
                        .mapToObj(index -> new NodeResolutionInput.ExistingSubject(
                                index + 1, reusableSubjects.get(index).getTitle()
                        ))
                .toList()
        );

        NodeResolutionResult result = resolutionLlmService.resolve(input);
        List<ResolvedNode> resolved = materialize(
                source.getUserId(), unresolved, reusableSubjects, result);
        log.info(
                "[node-resolution] sourceId={} finished resolvedSubjects={}",
                source.getId(), resolved.stream().map(item -> item.node().getTitle()).toList()
        );
        return resolved;
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
        Map<String, KnowledgeNode> selected = new LinkedHashMap<>();
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
            similar.forEach(subject -> selected.putIfAbsent(
                    NodeTitleNormalizer.normalize(subject.getTitle()), subject));
        }
        return new CandidateSearchResults(byCandidate, List.copyOf(selected.values()));
    }

    /** 직접 Subject 검색 순서를 우선하고 Source 검색 결과를 뒤에 더한다. */
    private List<KnowledgeNode> unionSubjects(
            List<KnowledgeNode> directSubjects,
            List<KnowledgeNode> sourceSubjects
    ) {
        Map<String, KnowledgeNode> union = new LinkedHashMap<>();
        directSubjects.forEach(subject -> union.putIfAbsent(
                NodeTitleNormalizer.normalize(subject.getTitle()), subject));
        sourceSubjects.forEach(subject -> union.putIfAbsent(
                NodeTitleNormalizer.normalize(subject.getTitle()), subject));
        return List.copyOf(union.values());
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

    /**
     * LLM 결정을 검증하고 재사용 또는 생성된 실제 Subject 노드로 확정한다.
     *
     * <p>어긋난 결정은 그 후보만 버리고 나머지로 계속한다. 후보 하나 때문에 링크 저장 전체가
     * 실패하지 않게 하려는 것이다. 다만 쓸 수 있는 결정이 하나도 없으면 응답을 통째로
     * 잘못 이해한 것이므로 실패로 두어 재시도 여지를 남긴다.
     */
    private List<ResolvedNode> materialize(
            Long userId,
            List<Candidate> unresolved,
            List<KnowledgeNode> existingSubjects,
            NodeResolutionResult result
    ) {
        if (result == null || result.decisions() == null) {
            throw new IllegalStateException("node resolution result is empty");
        }

        Map<Integer, NodeResolutionResult.Decision> decisions = new LinkedHashMap<>();
        for (NodeResolutionResult.Decision decision : result.decisions()) {
            String rejection = rejectionOf(decision, unresolved.size(), existingSubjects.size());
            if (rejection != null) {
                log.info("[node-resolution] dropped a decision: {}", rejection);
                continue;
            }
            if (decisions.putIfAbsent(decision.candidateIndex(), decision) != null) {
                log.info(
                        "[node-resolution] dropped a duplicate decision for candidate index {}",
                        decision.candidateIndex()
                );
            }
        }

        // LLM을 기다리는 동안 다른 요청이 같은 Subject를 만들었을 수 있다. 신규 판정 후보를
        // 모아 여기서 한 번에 다시 읽는다. 후보마다 읽으면 그만큼 왕복이 생긴다.
        Map<String, KnowledgeNode> createdMeanwhile = nodeService.findSubjectsByNormalizedTitles(
                userId,
                decisions.values().stream()
                        .filter(decision -> decision.action() == NodeResolutionResult.Action.CREATE)
                        .map(NodeResolutionService::newSubjectTitle)
                        .map(NodeTitleNormalizer::normalize)
                        .filter(normalized -> !normalized.isEmpty())
                        .distinct()
                        .toList()
        );

        List<ResolvedNode> resolved = new ArrayList<>();
        for (int index = 0; index < unresolved.size(); index++) {
            Candidate candidate = unresolved.get(index);
            NodeResolutionResult.Decision decision = decisions.get(index + 1);
            if (decision == null) {
                log.info("[node-resolution] no decision for candidate: {}", candidate.value());
                continue;
            }

            resolved.add(switch (decision.action()) {
                case REUSE -> reuse(candidate, decision, existingSubjects);
                case CREATE -> create(userId, candidate, decision, createdMeanwhile);
            });
        }

        if (resolved.isEmpty()) {
            throw new IllegalStateException("node resolution returned no usable decision");
        }
        return resolved;
    }

    /**
     * 쓸 수 없는 결정이면 이유를, 쓸 수 있으면 {@code null}을 준다.
     *
     * <p>노드를 만들기 전에 형식을 모두 확인해 두어야 뒤 단계에서 후보를 버리지 않는다.
     */
    private static String rejectionOf(
            NodeResolutionResult.Decision decision,
            int candidateCount,
            int existingSubjectCount
    ) {
        if (decision == null || decision.action() == null) {
            return "missing action";
        }
        if (decision.candidateIndex() < 1 || decision.candidateIndex() > candidateCount) {
            return "unknown candidate index " + decision.candidateIndex();
        }

        // REUSE의 value는 읽지 않는다. 재사용 대상은 reuseIndex가 정하므로, 모델이 대상 이름을
        // 적어 보내도 버릴 이유가 없다.
        return switch (decision.action()) {
            case REUSE -> decision.reuseIndex() < 1
                    || decision.reuseIndex() > existingSubjectCount
                    ? "invalid reuse index for candidate index " + decision.candidateIndex()
                    : null;
            case CREATE -> decision.reuseIndex() != 0
                    || NodeTitleNormalizer.normalize(newSubjectTitle(decision)).isEmpty()
                    ? "invalid new subject for candidate index " + decision.candidateIndex()
                    : null;
        };
    }

    /** 신규 판정이 내놓은 제목. 형식이 어긋나면 {@link #rejectionOf}가 걸러낸다. */
    private static String newSubjectTitle(NodeResolutionResult.Decision decision) {
        return decision.value() == null ? "" : decision.value().strip();
    }

    /** LLM이 고른 Context 안의 Subject를 재사용한다. */
    private ResolvedNode reuse(
            Candidate candidate,
            NodeResolutionResult.Decision decision,
            List<KnowledgeNode> existingSubjects
    ) {
        KnowledgeNode existing = existingSubjects.get(decision.reuseIndex() - 1);
        return ResolvedNode.semantic(candidate.value(), existing);
    }

    /** 신규 Subject를 만든다. 동시에 생성된 중복이 있으면 기존 노드를 재사용한다. */
    private ResolvedNode create(
            Long userId,
            Candidate candidate,
            NodeResolutionResult.Decision decision,
            Map<String, KnowledgeNode> createdMeanwhile
    ) {
        String value = newSubjectTitle(decision);
        String normalized = NodeTitleNormalizer.normalize(value);
        KnowledgeNode existing = createdMeanwhile.get(normalized);
        if (existing != null) {
            return ResolvedNode.exact(candidate.value(), existing);
        }
        return createSubject(userId, candidate.value(), value);
    }

    /** Subject title embedding을 먼저 계산하고, 노드 생성 직후 DB에 저장한다. */
    private ResolvedNode createSubject(Long userId, String candidate, String title) {
        float[] titleEmbedding = embedOne(embeddingText(title), "subject title");
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

    private record CandidateSearchResults(
            Map<Integer, List<KnowledgeNode>> byCandidate,
            List<KnowledgeNode> subjects
    ) {
    }

}
