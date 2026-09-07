package com.swimming.backend.knowledge.dto.in;

import com.swimming.backend.knowledge.domain.KnowledgeNode;

import java.util.UUID;

/**
 * 노드 하나를 가리키는 참조.
 *
 * <p>이름만이 아니라 id를 함께 주는 이유는, 눌러서 바로 Node Detail로 갈 수 있어야 하기
 * 때문이다. Source 카드 · Node Detail · Graph 세 곳이 같은 모양을 쓰므로 한 자리에 둔다.
 */
public record NodeRef(UUID nodeId, String title) {

    public static NodeRef from(KnowledgeNode node) {
        return new NodeRef(node.getId(), node.getTitle());
    }
}
