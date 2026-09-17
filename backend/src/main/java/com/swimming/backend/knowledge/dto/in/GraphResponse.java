package com.swimming.backend.knowledge.dto.in;

import com.swimming.backend.folder.dto.FolderReference;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeRelation;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.RelationType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Graph Browser가 한 화면에 그리는 서브그래프.
 *
 * <p>Folder 진입(§6.1)과 노드 확장(§6.2)이 이 하나를 쓴다. 그리는 자리가 같으므로 응답이
 * 갈라질 이유가 없고, {@code root}만 Folder냐 노드냐로 달라진다.
 *
 * @param truncated 상한에 걸려 잘렸다는 뜻. 노드 확장에는 상한이 없어 항상 false다
 */
public record GraphResponse(
        Root root,
        List<Node> nodes,
        List<Edge> edges,
        boolean truncated
) {

    /**
     * 그래프의 진입점.
     *
     * <p>Folder가 {@code knowledge_node}의 행이 아니라 {@link NodeType}에 자리가 없으므로,
     * 루트에만 {@code FOLDER}를 더한 타입을 쓴다. Folder → Source 간선은 {@code edges}에
     * 넣지 않는다. 루트에 달린 {@code SOURCE} 노드가 곧 소속이다.
     *
     * @param nodeId 노드 루트일 때만 채운다. Folder는 노드가 아니라 null이다
     * @param id     Folder 루트일 때만 채운다
     */
    public record Root(UUID nodeId, RootType type, Long id, String title) {

        public static Root of(FolderReference folder) {
            return new Root(null, RootType.FOLDER, folder.id(), folder.name());
        }

        public static Root of(KnowledgeNode node) {
            return new Root(node.getId(), RootType.of(node.getNodeType()), null, node.getTitle());
        }
    }

    public enum RootType {

        FOLDER,
        CATEGORY,
        SOURCE,
        SUBJECT,
        TOPIC;

        static RootType of(NodeType nodeType) {
            return valueOf(nodeType.name());
        }
    }

    /** @param createdAt 이 노드가 그래프에 처음 생긴 시각. 최근에 넓어진 곳을 짚는 데 쓴다 */
    public record Node(UUID nodeId, NodeType type, String title, Instant createdAt) {

        public static Node from(KnowledgeNode node) {
            return new Node(
                    node.getId(),
                    node.getNodeType(),
                    node.getTitle(),
                    node.getCreatedAt()
            );
        }
    }

    public record Edge(UUID from, UUID to, RelationType kind) {

        public static Edge from(KnowledgeRelation relation) {
            return new Edge(
                    relation.getFromNodeId(),
                    relation.getToNodeId(),
                    relation.getRelationType()
            );
        }
    }
}
