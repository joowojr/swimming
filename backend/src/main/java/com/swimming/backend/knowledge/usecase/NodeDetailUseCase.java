package com.swimming.backend.knowledge.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeRelation;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.RelationType;
import com.swimming.backend.knowledge.dto.in.NodeDetailResponse;
import com.swimming.backend.knowledge.dto.in.NodeRef;
import com.swimming.backend.knowledge.service.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.KnowledgeRelationService;
import com.swimming.backend.knowledge.service.KnowledgeSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Subject와 Topic Detail. 노드 하나에 걸린 문서와 개념을 모은다.
 *
 * <p>문서는 어느 쪽이든 역방향 한 번으로 읽는다. {@code ABOUT}은 Subject를, {@code SUPPORTS}는
 * Topic을 가리키므로 두 관계를 함께 물어도 결과가 섞이지 않는다.
 */
@Service
@RequiredArgsConstructor
public class NodeDetailUseCase {

    /** 이 노드를 가리키는 Source. Subject면 ABOUT, Topic이면 SUPPORTS로 걸린다. */
    private static final List<RelationType> FROM_SOURCE =
            List.of(RelationType.ABOUT, RelationType.SUPPORTS);

    private static final List<RelationType> TOPIC_TO_SUBJECT = List.of(RelationType.INVOLVES);

    private final KnowledgeNodeService nodeService;
    private final KnowledgeRelationService relationService;
    private final KnowledgeSourceService sourceService;

    public NodeDetailResponse get(Long userId, UUID nodeId) {
        KnowledgeNode node = nodeService.getOwned(nodeId, userId);

        // Source는 이 화면이 아니라 /api/knowledge/sources/{sourceId}가 맡는다. Graph 응답이
        // 노드 타입을 함께 주므로 잘못 부를 이유가 없고, 400이 아니라 404로 돌려준다.
        if (node.getNodeType() == NodeType.SOURCE) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND);
        }

        return NodeDetailResponse.of(
                node,
                sourcesOf(nodeId),
                node.getNodeType() == NodeType.SUBJECT ? topicsOf(nodeId) : List.of(),
                node.getNodeType() == NodeType.TOPIC ? subjectsOf(nodeId) : List.of()
        );
    }

    /** @return 최근 순. Topic이면 항상 한 개다 */
    private List<NodeDetailResponse.SourceRef> sourcesOf(UUID nodeId) {
        List<UUID> sourceIds = fromNodeIds(
                relationService.findIncoming(List.of(nodeId), FROM_SOURCE)
        );

        return sourceService.findAllByIds(sourceIds).stream()
                .sorted(Comparator
                        .comparing((KnowledgeSource source) -> source.getNode().getCreatedAt())
                        .thenComparing(KnowledgeSource::getId)
                        .reversed())
                .map(NodeDetailResponse.SourceRef::from)
                .toList();
    }

    /** 이 개념이 걸리는 목적. INVOLVES는 Topic에서 Subject로 향하므로 역방향으로 읽는다. */
    private List<NodeRef> topicsOf(UUID subjectId) {
        return refsOf(fromNodeIds(
                relationService.findIncoming(List.of(subjectId), TOPIC_TO_SUBJECT)
        ));
    }

    /** 이 목적이 걸치는 개념. */
    private List<NodeRef> subjectsOf(UUID topicId) {
        return refsOf(
                relationService.findOutgoing(List.of(topicId), TOPIC_TO_SUBJECT).stream()
                        .map(KnowledgeRelation::getToNodeId)
                        .toList()
        );
    }

    private List<UUID> fromNodeIds(List<KnowledgeRelation> relations) {
        return relations.stream().map(KnowledgeRelation::getFromNodeId).distinct().toList();
    }

    private List<NodeRef> refsOf(List<UUID> nodeIds) {
        return nodeService.findAllByIds(nodeIds).stream().map(NodeRef::from).toList();
    }
}
