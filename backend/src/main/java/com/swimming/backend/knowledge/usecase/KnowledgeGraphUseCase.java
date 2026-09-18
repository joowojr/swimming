package com.swimming.backend.knowledge.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.folder.dto.FolderReference;
import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeRelation;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.RelationType;
import com.swimming.backend.knowledge.dto.in.GraphResponse;
import com.swimming.backend.knowledge.repository.SourcePageQuery;
import com.swimming.backend.knowledge.service.graph.KnowledgeGraphAssembler;
import com.swimming.backend.knowledge.service.data.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.data.KnowledgeRelationService;
import com.swimming.backend.knowledge.service.data.KnowledgeSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Graph Browser가 한 번에 그릴 만큼만 잘라 준다.
 *
 * <p>Folder의 모든 Source를 펼치지 않는다. 진입할 때는 최근 Source부터 상한만큼 보여 주고,
 * 나머지는 노드를 눌러 넓혀 간다.
 */
@Service
@RequiredArgsConstructor
public class KnowledgeGraphUseCase {

    public static final int MIN_DEPTH = 1;
    public static final int MAX_DEPTH = 2;

    /** Source에서 나가는 관계. 개념과 목적을 함께 읽는다. */
    private static final List<RelationType> FROM_SOURCE =
            List.of(RelationType.ABOUT, RelationType.SUPPORTS);

    private static final List<RelationType> TOPIC_TO_SUBJECT = List.of(RelationType.INVOLVES);

    private final FolderService folderService;
    private final KnowledgeSourceService sourceService;
    private final KnowledgeNodeService nodeService;
    private final KnowledgeRelationService relationService;
    private final KnowledgeGraphAssembler assembler;

    /**
     * Folder로 진입한 초기 서브그래프.
     *
     * <p>대표 Source는 우선 최근순으로 고른다. 소화가 끝나지 않은 Source도 노드로는 나오고
     * 간선만 없다.
     */
    public GraphResponse ofFolder(Long userId, Long folderId, int limit) {
        FolderReference folder = folderService.getReference(userId, folderId);

        // 한 건 더 읽어 상한에 걸렸는지 본다. 총 개수를 세지 않아도 된다.
        List<KnowledgeSource> fetched = sourceService.findPage(
                new SourcePageQuery(userId, folderId, null, limit + 1, null, null)
        );

        boolean truncated = fetched.size() > limit;
        List<KnowledgeSource> sources = truncated ? fetched.subList(0, limit) : fetched;

        List<KnowledgeRelation> fromSources = relationService.findOutgoing(
                sources.stream().map(KnowledgeSource::getId).toList(),
                FROM_SOURCE
        );

        // Topic이 걸치는 개념까지 한 겹 더 읽는다. 그래야 Source를 거치지 않는 Topic → Subject
        // 간선이 화면에 남는다.
        List<KnowledgeRelation> fromTopics = relationService.findOutgoing(
                endpointsOf(fromSources, NodeType.TOPIC),
                TOPIC_TO_SUBJECT
        );

        // 상한 안의 문서를 담는 카테고리만 읽는다. 카테고리의 다른 문서까지 펼치지 않는다.
        List<KnowledgeRelation> fromCategories = relationService.findIncoming(
                sources.stream().map(KnowledgeSource::getId).toList(),
                List.of(RelationType.CONTAINS)
        );

        return assembler.assemble(
                GraphResponse.Root.of(folder),
                sources.stream().map(KnowledgeSource::getNode).toList(),
                Stream.of(fromSources, fromTopics, fromCategories).flatMap(List::stream).toList(),
                truncated
        );
    }

    /**
     * 노드를 눌렀을 때의 1-hop, {@code 더 보기}의 2-hop.
     *
     * <p>2-hop은 1-hop에서 만난 개념·목적에 걸린 문서를 붙인다. 개념 계층이 없으므로
     * {@code Subject → Topic → Source}와 {@code Topic → Subject → Source} 두 방향뿐이고
     * 그 이상은 없다. 이 규칙 하나가 두 방향을 모두 덮는다.
     *
     * <p>상한이 없어 {@code truncated}는 항상 false다.
     */
    public GraphResponse ofNode(Long userId, UUID nodeId, int depth) {
        if (depth < MIN_DEPTH || depth > MAX_DEPTH) {
            throw new BusinessException(ErrorCode.INVALID_GRAPH_DEPTH);
        }

        KnowledgeNode root = nodeService.getOwned(nodeId, userId);

        List<KnowledgeRelation> relations = new ArrayList<>(firstHop(root));

        if (depth == MAX_DEPTH) {
            Set<UUID> concepts = new LinkedHashSet<>(endpointsOf(relations, NodeType.SUBJECT));
            concepts.addAll(endpointsOf(relations, NodeType.TOPIC));
            concepts.remove(nodeId);

            relations.addAll(relationService.findIncoming(concepts, FROM_SOURCE));
        }

        return assembler.assemble(
                GraphResponse.Root.of(root),
                List.of(root),
                relations,
                false
        );
    }

    /** 루트 타입마다 어느 방향으로 읽는지가 다르다. */
    private List<KnowledgeRelation> firstHop(KnowledgeNode root) {
        List<UUID> rootId = List.of(root.getId());

        return switch (root.getNodeType()) {
            case SOURCE -> Stream.concat(
                    relationService.findOutgoing(rootId, FROM_SOURCE).stream(),
                    relationService.findIncoming(rootId, List.of(RelationType.CONTAINS)).stream()
            ).toList();
            case SUBJECT -> relationService.findIncoming(
                    rootId,
                    List.of(RelationType.ABOUT, RelationType.INVOLVES)
            );
            case TOPIC -> Stream.concat(
                    relationService.findIncoming(rootId, List.of(RelationType.SUPPORTS)).stream(),
                    relationService.findOutgoing(rootId, TOPIC_TO_SUBJECT).stream()
            ).toList();
            // 묶음에서 읽을 것은 담긴 문서뿐이다. Category는 다른 노드와 이어지지 않는다.
            case CATEGORY -> relationService.findOutgoing(rootId, List.of(RelationType.CONTAINS));
        };
    }

    /**
     * 관계의 양끝에서 이 타입인 노드의 id만 모은다.
     *
     * <p>관계 타입이 양끝의 노드 타입을 정하므로, 노드를 다시 읽지 않고도 어느 쪽이
     * Topic인지 알 수 있다.
     */
    private List<UUID> endpointsOf(List<KnowledgeRelation> relations, NodeType nodeType) {
        Set<UUID> ids = new LinkedHashSet<>();

        for (KnowledgeRelation relation : relations) {
            RelationType relationType = relation.getRelationType();

            if (relationType.getFromType() == nodeType) {
                ids.add(relation.getFromNodeId());
            }

            if (relationType.getToType() == nodeType) {
                ids.add(relation.getToNodeId());
            }
        }

        return List.copyOf(ids);
    }
}
