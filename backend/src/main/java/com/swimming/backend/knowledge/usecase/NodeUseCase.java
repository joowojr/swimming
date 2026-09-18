package com.swimming.backend.knowledge.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeRelation;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.RelationOrigin;
import com.swimming.backend.knowledge.domain.RelationType;
import com.swimming.backend.knowledge.dto.in.NodeDetailResponse;
import com.swimming.backend.knowledge.dto.in.NodeRef;
import com.swimming.backend.knowledge.service.data.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.data.KnowledgeRelationService;
import com.swimming.backend.knowledge.service.data.KnowledgeSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Subject·Topic 상세, Subject 삭제, Category·Topic 이름 수정을 조율한다.
 *
 * <p>Source 상세는 {@link SourceQueryUseCase}가 맡으므로 상세 조회에서는 404로 돌려준다.
 * 이름 수정은 Category·Topic만 허용한다.
 */
@Service
@RequiredArgsConstructor
public class NodeUseCase {

    /** 이 노드를 가리키는 Source. Subject면 ABOUT, Topic이면 SUPPORTS로 걸린다. */
    private static final List<RelationType> FROM_SOURCE =
            List.of(RelationType.ABOUT, RelationType.SUPPORTS);

    private static final List<RelationType> TOPIC_TO_SUBJECT = List.of(RelationType.INVOLVES);

    private final KnowledgeNodeService nodeService;
    private final KnowledgeRelationService relationService;
    private final KnowledgeSourceService sourceService;
    private final FolderService folderService;

    /**
     * Category와 Topic의 제목을 바꾼다.
     *
     * <p>같은 폴더에 같은 이름의 Category가 이미 있으면 거절하지 않고 <b>그쪽으로 합친다.</b>
     * 담고 있던 Source를 기존 Category로 옮기고 이름을 바꾸려던 Category는 지운다.
     * 사용자가 같은 이름을 붙였다는 것은 같은 묶음으로 보겠다는 뜻이기 때문이다.
     *
     * <p>합쳐지면 <b>돌려주는 노드의 id가 요청한 id와 달라진다.</b> 부르는 쪽은 응답의 id로
     * 가리키던 것을 바꿔야 한다.
     *
     * <p>Topic은 합치지 않는다. Topic 하나가 가리키는 Source는 항상 하나라는 계약이 있어
     * (기능정의서 §11.1) 둘을 합치면 그 계약이 깨진다.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public NodeRef updateTitle(Long userId, UUID nodeId, String title) {
        KnowledgeNode node = nodeService.getOwned(nodeId, userId);
        node.renameByUser(title, Instant.now());

        if (node.getNodeType() == NodeType.CATEGORY) {
            Optional<KnowledgeNode> duplicate = findDuplicateCategory(userId, node);
            if (duplicate.isPresent()) {
                return NodeRef.from(mergeCategory(node, duplicate.get()));
            }
        }

        nodeService.updateTitle(node);
        return NodeRef.from(node);
    }

    /**
     * 같은 폴더에서 정규화 이름이 같은 다른 Category를 찾는다.
     *
     * <p>Replace·소화 중 배정과 같은 폴더 잠금을 잡는다. 잠그지 않으면 그 사이 같은 이름의
     * Category가 생겨 중복이 저장될 수 있다. 담긴 Source가 없는 Category는 어느 폴더에서도
     * 조회되지 않으므로 비교할 대상이 없다.
     */
    private Optional<KnowledgeNode> findDuplicateCategory(Long userId, KnowledgeNode node) {
        Optional<Long> folderId = folderOf(userId, node.getId());
        if (folderId.isEmpty()) {
            return Optional.empty();
        }

        folderService.lockOwned(userId, folderId.get());
        return nodeService.findCategoriesInFolder(userId, folderId.get()).stream()
                .filter(other -> !other.getId().equals(node.getId())
                        && other.getNormalizedTitle().equals(node.getNormalizedTitle()))
                .findFirst();
    }

    /**
     * 담고 있던 Source를 기존 Category로 옮기고 원래 Category를 지운다.
     *
     * <p>근거는 Replace와 같이 {@code USER}로 둔다. 어느 묶음에 담을지를 사용자가 이름으로
     * 정했기 때문이다. 같은 Source가 이미 담겨 있으면 저장소가 자연키로 합쳐 준다.
     */
    private KnowledgeNode mergeCategory(KnowledgeNode from, KnowledgeNode into) {
        List<UUID> sourceIds = relationService
                .findOutgoing(List.of(from.getId()), List.of(RelationType.CONTAINS))
                .stream().map(KnowledgeRelation::getToNodeId).toList();

        relationService.connectAll(into, nodeService.findAllByIds(sourceIds), RelationOrigin.USER);
        relationService.disconnectAllFrom(from.getId(), RelationType.CONTAINS);
        from.delete();
        nodeService.delete(from);
        return into;
    }

    /** Category의 폴더는 담긴 Source에서 파생한다. 한 Category의 Source는 모두 같은 폴더다. */
    private Optional<Long> folderOf(Long userId, UUID categoryId) {
        List<UUID> sourceIds = relationService.findOutgoing(List.of(categoryId), List.of(RelationType.CONTAINS))
                .stream().map(KnowledgeRelation::getToNodeId).toList();
        if (sourceIds.isEmpty()) {
            return Optional.empty();
        }
        return sourceService.getOwnedAll(userId, sourceIds).stream()
                .map(KnowledgeSource::getFolderId)
                .findFirst();
    }

    /**
     * Subject·Topic·Category Detail. 노드 하나에 걸린 문서와 개념을 모은다.
     *
     * <p>Subject·Topic의 문서는 역방향 한 번으로 읽는다. {@code ABOUT}은 Subject를,
     * {@code SUPPORTS}는 Topic을 가리키므로 두 관계를 함께 물어도 결과가 섞이지 않는다.
     * Category는 {@code CONTAINS}로 문서를 가리키므로 정방향으로 읽는다.
     */
    public NodeDetailResponse get(Long userId, UUID nodeId) {
        KnowledgeNode node = requireConceptNode(nodeService.getOwned(nodeId, userId));

        return NodeDetailResponse.of(
                node,
                sourcesOf(node),
                node.getNodeType() == NodeType.SUBJECT ? topicsOf(nodeId) : List.of(),
                node.getNodeType() == NodeType.TOPIC ? subjectsOf(nodeId) : List.of()
        );
    }

    /**
     * 개념 노드를 지운다.
     *
     * <p>문서는 건드리지 않는다. 개념을 지워도 그 개념을 다루던 문서는 그대로 남고, 카드에서
     * 그 이름만 사라진다.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void delete(Long userId, UUID nodeId) {
        KnowledgeNode node = requireConceptNode(nodeService.getOwned(nodeId, userId));

        // Topic을 혼자 지우면 소화가 끝난 Source가 목적을 잃는다. Topic이 사라지는 길은
        // 그 Source를 지우는 것 하나뿐이다.
        if (node.getNodeType() == NodeType.TOPIC) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_TOPIC_NOT_DELETABLE);
        }
        // Category는 폴더 구성의 일부라 Replace로만 바뀐다. 하나만 지우면 담긴 Source가
        // 미분류로 남는데, 구성이 생긴 뒤의 미분류는 정상 상태가 아니다.
        if (node.getNodeType() == NodeType.CATEGORY) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_CATEGORY_NOT_DELETABLE);
        }
        node.delete();
        nodeService.delete(node);
    }

    private KnowledgeNode requireConceptNode(KnowledgeNode node) {
        if (node.getNodeType() == NodeType.SOURCE) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND);
        }

        return node;
    }

    /** @return 최근 순. Topic이면 항상 한 개다 */
    private List<NodeDetailResponse.SourceRef> sourcesOf(KnowledgeNode node) {
        List<UUID> sourceIds = node.getNodeType() == NodeType.CATEGORY
                ? relationService.findOutgoing(List.of(node.getId()), List.of(RelationType.CONTAINS)).stream()
                        .map(KnowledgeRelation::getToNodeId)
                        .distinct()
                        .toList()
                : fromNodeIds(relationService.findIncoming(List.of(node.getId()), FROM_SOURCE));

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
