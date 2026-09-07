package com.swimming.backend.knowledge.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.service.KnowledgeNodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 개념 노드를 지운다.
 *
 * <p>문서는 건드리지 않는다. 개념을 지워도 그 개념을 다루던 문서는 그대로 남고, 카드에서
 * 그 이름만 사라진다.
 */
@Service
@RequiredArgsConstructor
public class NodeDeleteUseCase {

    private final KnowledgeNodeService nodeService;

    @Transactional(propagation = Propagation.REQUIRED)
    public void delete(Long userId, UUID nodeId) {
        KnowledgeNode node = nodeService.getOwned(nodeId, userId);

        // Source는 이 경로가 맡지 않는다. Node Detail과 같은 규칙이다.
        if (node.getNodeType() == NodeType.SOURCE) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND);
        }

        // Topic을 혼자 지우면 소화가 끝난 Source가 목적을 잃는다. Topic이 사라지는 길은
        // 그 Source를 지우는 것 하나뿐이다.
        if (node.getNodeType() == NodeType.TOPIC) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_TOPIC_NOT_DELETABLE);
        }

        nodeService.delete(node);
    }
}
