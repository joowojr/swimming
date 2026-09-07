package com.swimming.backend.knowledge.service.graph;

import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeRelation;
import com.swimming.backend.knowledge.dto.in.GraphResponse;
import com.swimming.backend.knowledge.service.data.KnowledgeNodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 관계를 그래프의 노드와 간선으로 옮긴다. 그래프 응답이 어떤 모양인지 아는 곳은 여기 하나다.
 *
 * <p>Folder 진입과 노드 확장이 읽는 관계는 다르지만 조립은 같다. 어느 관계를 읽을지는
 * UseCase가 정하고, 여기서는 받은 관계만 편다.
 */
@Service
@RequiredArgsConstructor
public class KnowledgeGraphAssembler {

    private final KnowledgeNodeService nodeService;

    /**
     * @param seeds     이미 읽어 둔 노드. 간선이 하나도 없어도 그래프에 남는다. 소화가 끝나지
     *                  않은 Source가 홀로 나오는 자리다
     * @param relations 간선이 될 관계. 여기 등장하는 나머지 노드는 이름을 찾아 채운다
     */
    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public GraphResponse assemble(
            GraphResponse.Root root,
            List<KnowledgeNode> seeds,
            Collection<KnowledgeRelation> relations,
            boolean truncated
    ) {
        Map<UUID, GraphResponse.Node> nodes = new LinkedHashMap<>();
        for (KnowledgeNode seed : seeds) {
            nodes.put(seed.getId(), GraphResponse.Node.from(seed));
        }

        for (KnowledgeNode node : nodeService.findAllByIds(missingIds(nodes.keySet(), relations))) {
            nodes.put(node.getId(), GraphResponse.Node.from(node));
        }

        // 이름을 못 찾은 노드는 그릴 수 없다. 그 노드에 걸린 간선도 함께 뺀다.
        // 한쪽 끝이 없는 간선을 남기면 클라이언트가 허공에 선을 긋는다.
        Set<GraphResponse.Edge> edges = new LinkedHashSet<>();
        for (KnowledgeRelation relation : relations) {
            if (nodes.containsKey(relation.getFromNodeId())
                    && nodes.containsKey(relation.getToNodeId())) {
                edges.add(GraphResponse.Edge.from(relation));
            }
        }

        return new GraphResponse(root, List.copyOf(nodes.values()), List.copyOf(edges), truncated);
    }

    /** 관계의 양끝 중 아직 읽지 않은 노드. 이름을 찾으러 한 번만 나간다. */
    private List<UUID> missingIds(Set<UUID> known, Collection<KnowledgeRelation> relations) {
        Set<UUID> missing = new LinkedHashSet<>();

        for (KnowledgeRelation relation : relations) {
            missing.add(relation.getFromNodeId());
            missing.add(relation.getToNodeId());
        }

        missing.removeAll(known);

        return new ArrayList<>(missing);
    }
}
