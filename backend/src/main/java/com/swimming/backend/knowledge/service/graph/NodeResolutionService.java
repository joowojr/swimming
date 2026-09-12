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
import com.swimming.backend.knowledge.service.data.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.data.KnowledgeRelationService;
import com.swimming.backend.knowledge.service.data.KnowledgeSourceService;
import com.swimming.backend.common.client.EmbeddingClient;
import com.swimming.backend.knowledge.service.llm.NodeResolutionLlmService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
@RequiredArgsConstructor
public class NodeResolutionService {

    static final String EMBEDDING_MODEL = "text-embedding-3-small";
    static final int EMBEDDING_DIMENSIONS = 768;

    private final EmbeddingClient embeddingClient;
    private final ResolutionProperties properties;
    private final KnowledgeSourceService sourceService;
    private final KnowledgeNodeService nodeService;
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
            return List.of();
        }

        Map<String, ResolvedNode> resolved = new LinkedHashMap<>();
        List<Candidate> unresolved = new ArrayList<>();

        Map<String, KnowledgeNode> exactMatches = nodeService.findSubjectsByNormalizedTitles(
                source.getUserId(),
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

        if (!unresolved.isEmpty()) {
            for (ResolvedNode item : resolveSemantically(
                    source, summary, summaryEmbedding, unresolved
            )) {
                resolved.put(NodeTitleNormalizer.normalize(item.candidate()), item);
            }
        }

        return deduplicateNodes(candidates.stream()
                .map(candidate -> resolved.get(candidate.normalized()))
                .toList());
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
        CompletableFuture<List<KnowledgeNode>> subjectSearchFuture = CompletableFuture.supplyAsync(
                () -> subjectsFromEmbedding(source.getUserId(), unresolved));
        CompletableFuture.allOf(sourceSearchFuture, subjectSearchFuture).join();

        List<KnowledgeNode> existingSubjects = unionSubjects(
                subjectSearchFuture.join(), sourceSearchFuture.join());

        NodeResolutionInput input = new NodeResolutionInput(
                summary,
                IntStream.range(0, unresolved.size())
                        .mapToObj(index -> new NodeResolutionInput.Candidate(
                                index + 1, unresolved.get(index).value()
                        ))
                        .toList(),
                IntStream.range(0, existingSubjects.size())
                        .mapToObj(index -> new NodeResolutionInput.ExistingSubject(
                                index + 1, existingSubjects.get(index).getTitle()
                        ))
                        .toList()
        );

        NodeResolutionResult result = resolutionLlmService.resolve(input);
        return materialize(source.getUserId(), unresolved, existingSubjects, result);
    }

    private List<KnowledgeNode> subjectsFromSimilarSources(
            KnowledgeSource source,
            float[] summaryEmbedding
    ) {
        List<UUID> similarSourceIds = sourceService.findSimilarSourceIds(
                source.getUserId(),
                source.getId(),
                summaryEmbedding,
                EMBEDDING_MODEL,
                properties.similarSourceLimit()
        );
        return List.copyOf(subjectsOf(source.getUserId(), similarSourceIds).values());
    }

    /** 미해결 후보만 임베딩하고 DB에 저장된 Subject embedding 상위 K개를 수집한다. */
    private List<KnowledgeNode> subjectsFromEmbedding(
            Long userId,
            List<Candidate> unresolved
    ) {
        List<String> inputs = unresolved.stream()
                .map(Candidate::value)
                .map(this::embeddingText)
                .toList();
        List<float[]> embeddings = embeddingClient.embed(inputs);
        validateEmbeddings(embeddings, inputs.size());

        Map<String, KnowledgeNode> selected = new LinkedHashMap<>();
        for (int candidateIndex = 0; candidateIndex < unresolved.size(); candidateIndex++) {
            nodeService.findSimilarSubjects(
                            userId,
                            embeddings.get(candidateIndex),
                            EMBEDDING_MODEL,
                            properties.subjectTopK()
                    )
                    .forEach(subject -> selected.putIfAbsent(
                            NodeTitleNormalizer.normalize(subject.getTitle()), subject));
        }
        return List.copyOf(selected.values());
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

    /** LLM 결정을 검증하고 재사용 또는 생성된 실제 Subject 노드로 확정한다. */
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
            if (decision == null || decision.action() == null
                    || decision.candidateIndex() < 1 || decision.candidateIndex() > unresolved.size()) {
                throw new IllegalStateException("node resolution answered an unknown candidate index");
            }
            if (decisions.putIfAbsent(decision.candidateIndex(), decision) != null) {
                throw new IllegalStateException("node resolution contains duplicate decision");
            }
        }

        if (decisions.size() != unresolved.size()) {
            throw new IllegalStateException("node resolution decision count does not match candidates");
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

        // index가 1..N 안에서 중복 없이 N개 왔으므로 후보마다 결정이 정확히 하나씩 있다.
        List<ResolvedNode> resolved = new ArrayList<>();
        for (int index = 0; index < unresolved.size(); index++) {
            Candidate candidate = unresolved.get(index);
            NodeResolutionResult.Decision decision = decisions.get(index + 1);

            resolved.add(switch (decision.action()) {
                case REUSE -> reuse(candidate, decision, existingSubjects);
                case CREATE -> create(userId, candidate, decision, createdMeanwhile);
            });
        }
        return resolved;
    }

    /** 신규 판정이 내놓은 제목. 형식이 어긋나면 {@link #create}가 걸러내므로 여기서는 비워 둔다. */
    private static String newSubjectTitle(NodeResolutionResult.Decision decision) {
        return decision.value() == null ? "" : decision.value().strip();
    }

    /** LLM이 선택한 Subject가 제공된 Context에 속하는지 확인하고 재사용한다. */
    private ResolvedNode reuse(
            Candidate candidate,
            NodeResolutionResult.Decision decision,
            List<KnowledgeNode> existingSubjects
    ) {
        int index = decision.subjectIndex();
        if (index < 1 || index > existingSubjects.size()
                || decision.value() == null || !decision.value().isEmpty()) {
            throw new IllegalStateException("node resolution selected an invalid existing subject");
        }
        KnowledgeNode existing = existingSubjects.get(index - 1);
        return ResolvedNode.semantic(candidate.value(), existing);
    }

    /** 신규 Subject 제안을 검증하고 동시에 생성된 중복이 있으면 기존 노드를 재사용한다. */
    private ResolvedNode create(
            Long userId,
            Candidate candidate,
            NodeResolutionResult.Decision decision,
            Map<String, KnowledgeNode> createdMeanwhile
    ) {
        if (decision.subjectIndex() != 0 || !StringUtils.hasText(decision.value())) {
            throw new IllegalStateException("node resolution returned an invalid new subject");
        }

        String value = decision.value().strip();
        String normalized = NodeTitleNormalizer.normalize(value);
        if (normalized.isEmpty()) {
            throw new IllegalStateException("node resolution returned an empty new subject");
        }

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

}
