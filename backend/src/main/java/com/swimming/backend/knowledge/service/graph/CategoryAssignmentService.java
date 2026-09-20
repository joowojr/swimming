package com.swimming.backend.knowledge.service.graph;

import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.knowledge.domain.NodeTitleNormalizer;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.RelationType;
import com.swimming.backend.knowledge.domain.SourceProcessingStatus;
import com.swimming.backend.knowledge.dto.in.NodeRef;
import com.swimming.backend.knowledge.dto.out.CategoryAssignmentCandidate;
import com.swimming.backend.knowledge.dto.out.CategoryAssignmentDecision;
import com.swimming.backend.knowledge.dto.out.CategoryAssignmentInput;
import com.swimming.backend.knowledge.dto.out.SourceDigestResult;
import com.swimming.backend.knowledge.service.SourceGraphReader;
import com.swimming.backend.knowledge.service.data.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.data.KnowledgeRelationService;
import com.swimming.backend.knowledge.service.data.KnowledgeSourceService;
import com.swimming.backend.knowledge.service.llm.CategoryAssignmentDecider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 외부 판정과 저장을 조율한다. 모델 호출 동안 트랜잭션을 열지 않는다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryAssignmentService {
    static final int MIN_SOURCE_COUNT = 6;
    private static final int TOPIC_EXAMPLE_LIMIT = 3;

    private final FolderService folderService;
    private final KnowledgeNodeService nodeService;
    private final KnowledgeRelationService relationService;
    private final KnowledgeSourceService sourceService;
    private final SourceGraphReader sourceGraphReader;
    private final CategoryAssignmentDecider decider;
    private final SourceGraphWriter graphWriter;

    public void assign(KnowledgeSource source, SourceDigestResult digest) {
        if (source.getProcessingStatus() != SourceProcessingStatus.COMPLETED) return;
        try {
            if (folderService.getSourceCount(source.getUserId(), source.getFolderId()) < MIN_SOURCE_COUNT) return;
            var categoryNodes = nodeService.findCategoriesInFolder(source.getUserId(), source.getFolderId());
            var categories = categoryNodes.stream().map(NodeRef::from).toList();
            if (categories.isEmpty()) return;
            var sameTitle = sameTitle(digest.category(), categories);
            CategoryAssignmentDecision decision = sameTitle.isPresent()
                    ? new CategoryAssignmentDecision.Reuse(sameTitle.get().nodeId())
                    : decider.decide(new CategoryAssignmentInput(
                            source.getNode().getTitle(),
                            digest.summary(),
                            digest.category(),
                            candidatesWithTopics(source.getUserId(), categoryNodes)
                    ));
            if (!graphWriter.createCategoryAssignmentInTransaction(source, decision)) {
                log.info("[category-assignment] sourceId={} not assigned decision={}", source.getId(), decision);
            }
        } catch (RuntimeException e) {
            // 배정 오류로 소화 완료 결과를 버리지 않는다. 요약·원문·키는 로그에 넣지 않는다.
            log.warn("[category-assignment] failed sourceId={} errorType={}", source.getId(), e.getClass().getSimpleName());
        }
    }

    /** 기존 일괄 관계·그래프 조회 결과를 조합해 Category마다 최근 고유 Topic을 세 개까지 붙인다. */
    private List<CategoryAssignmentCandidate> candidatesWithTopics(
            Long userId,
            List<KnowledgeNode> categories
    ) {
        List<UUID> categoryIds = categories.stream().map(KnowledgeNode::getId).toList();
        var contains = relationService.findOutgoing(categoryIds, List.of(RelationType.CONTAINS));

        Map<UUID, UUID> categoryBySource = new LinkedHashMap<>();
        for (var relation : contains) {
            categoryBySource.put(relation.getToNodeId(), relation.getFromNodeId());
        }

        Map<UUID, List<String>> topicsByCategory = new HashMap<>();
        if (!categoryBySource.isEmpty()) {
            List<KnowledgeSource> sources = sourceService.getOwnedAll(userId, categoryBySource.keySet());
            var conceptsBySource = sourceGraphReader.readAll(sources);
            Map<UUID, Set<String>> normalizedTopicsByCategory = new HashMap<>();

            sources.stream()
                    .sorted(Comparator.comparing(
                            item -> item.getNode().getCreatedAt(),
                            Comparator.nullsLast(Comparator.reverseOrder())
                    ))
                    .forEach(item -> {
                        UUID categoryId = categoryBySource.get(item.getId());
                        var concepts = conceptsBySource.get(item.getId());
                        if (categoryId == null || concepts == null || concepts.topic() == null) return;

                        String topic = concepts.topic().title();
                        String normalizedTopic = NodeTitleNormalizer.normalize(topic);
                        if (normalizedTopic.isEmpty()) return;

                        List<String> topics = topicsByCategory.computeIfAbsent(categoryId, key -> new ArrayList<>());
                        Set<String> normalizedTopics = normalizedTopicsByCategory
                                .computeIfAbsent(categoryId, key -> new HashSet<>());
                        if (topics.size() < TOPIC_EXAMPLE_LIMIT && normalizedTopics.add(normalizedTopic)) {
                            topics.add(topic);
                        }
                    });
        }

        return categories.stream()
                .map(category -> new CategoryAssignmentCandidate(
                        category.getId(),
                        category.getTitle(),
                        topicsByCategory.getOrDefault(category.getId(), List.of())
                ))
                .toList();
    }

    /**
     * 제안 이름이 기존 Category와 정규화 기준으로 같으면 판정할 것이 없다. 모델을 부르지 않는다.
     *
     * <p>판정기가 아니라 여기서 한다. 어떤 판정기를 쓰든 같은 이름을 다른 묶음으로 보내면 안 된다.
     */
    private Optional<NodeRef> sameTitle(String proposedTitle, List<NodeRef> categories) {
        String normalized = NodeTitleNormalizer.normalize(proposedTitle);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return categories.stream()
                .filter(category -> NodeTitleNormalizer.normalize(category.title()).equals(normalized))
                .findFirst();
    }
}
