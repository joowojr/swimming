package com.swimming.backend.knowledge.service.graph;

import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeRelation;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.NodeTitleNormalizer;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.RelationOrigin;
import com.swimming.backend.knowledge.domain.RelationType;
import com.swimming.backend.knowledge.dto.out.CategoryAssignmentDecision;
import com.swimming.backend.knowledge.dto.out.ResolvedNode;
import com.swimming.backend.knowledge.dto.out.SourceDigestResult;
import com.swimming.backend.knowledge.service.data.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.data.KnowledgeRelationService;
import com.swimming.backend.knowledge.service.llm.SourceDigestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 소화 결과를 노드와 관계로 옮긴다. 그래프가 어떤 모양인지 아는 곳은 여기 하나다.
 *
 * <p>노드 생성과 관계 저장이 한 트랜잭션 안에서 끝난다. Subject만 만들어지고 관계가 빠진
 * 중간 상태를 남기지 않기 위해서다.
 */
@Service
@RequiredArgsConstructor
public class SourceGraphWriter {

    private final KnowledgeNodeService nodeService;
    private final KnowledgeRelationService relationService;
    private final FolderService folderService;

    /**
     * Subject는 기존 노드를 먼저 찾아 재사용하고, Topic은 Source당 하나이므로 매번 만든다.
     *
     * <p>{@code INVOLVES}는 LLM이 따로 내놓지 않는다. Source당 Topic이 하나여서 그 Source가
     * 다룬 Subject가 곧 그 Topic이 걸치는 개념이 되기 때문에, 여기서 파생시킨다.
     *
     * <p>Topic이 비어 있는 경우는 다루지 않는다. 그런 결과는 여기 오기 전에
     * {@link SourceDigestService}가 소화 실패로 돌린다.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void createDigestGraphInTransaction(
            KnowledgeSource source,
            SourceDigestResult result,
            List<ResolvedNode> resolvedSubjects
    ) {
        Long userId = source.getUserId();
        KnowledgeNode sourceNode = source.getNode();

        List<KnowledgeNode> subjects = resolvedSubjects.stream()
                .map(ResolvedNode::node)
                .toList();

        relationService.connectAll(sourceNode, subjects, RelationOrigin.AI);

        KnowledgeNode topic = nodeService.create(userId, NodeType.TOPIC, result.topic(), null);

        relationService.connect(sourceNode, topic, RelationOrigin.AI);
        relationService.connectAll(topic, subjects, RelationOrigin.AI);
    }

    /**
     * 판정 결과를 이 폴더의 Category 구성에 반영한다. 반영하지 않았으면 false.
     *
     * <p>판정은 트랜잭션 밖에서 수 초가 걸린다. 그 사이 Confirm이 구성을 바꿨을 수 있으므로
     * Confirm과 같은 폴더 잠금을 잡고 후보를 다시 읽어 판정을 검증한다.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public boolean createCategoryAssignmentInTransaction(KnowledgeSource source, CategoryAssignmentDecision decision) {
        Long userId = source.getUserId();
        folderService.lockOwned(userId, source.getFolderId());

        // Confirm이 구성을 모두 걷어냈으면 축이 없다. 문서 하나로 축을 새로 세우지 않는다.
        List<KnowledgeNode> categories = nodeService.findCategoriesInFolder(userId, source.getFolderId());
        if (categories.isEmpty()) {
            return false;
        }

        // 기다리는 동안 Confirm이 이 Source를 이미 담았으면 사용자의 배치를 따른다.
        Set<UUID> categoryIds = categories.stream().map(KnowledgeNode::getId).collect(Collectors.toSet());
        boolean alreadyContained = relationService.findIncoming(
                        List.of(source.getNode().getId()), List.of(RelationType.CONTAINS)
                ).stream()
                .map(KnowledgeRelation::getFromNodeId)
                .anyMatch(categoryIds::contains);
        if (alreadyContained) {
            return false;
        }

        KnowledgeNode category = switch (decision) {
            // 이 폴더의 살아 있는 Category가 아니면 그 배정을 쓰지 않는다.
            case CategoryAssignmentDecision.Reuse reuse -> categories.stream()
                    .filter(item -> item.getId().equals(reuse.categoryId()))
                    .findFirst()
                    .orElse(null);
            // 같은 묶음을 다른 표기로 부른 것이면 새로 만들지 않고 기존 것에 담는다.
            case CategoryAssignmentDecision.Create create -> categories.stream()
                    .filter(item -> item.getNormalizedTitle()
                            .equals(NodeTitleNormalizer.normalize(create.title())))
                    .findFirst()
                    .orElseGet(() -> nodeService.create(userId, NodeType.CATEGORY, create.title(), null));
            case CategoryAssignmentDecision.Skip skip -> null;
        };
        if (category == null) {
            return false;
        }

        relationService.connect(category, source.getNode(), RelationOrigin.AI);
        return true;
    }
}
