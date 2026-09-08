package com.swimming.backend.knowledge.service.data;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.repository.KnowledgeNodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KnowledgeNodeService {

    private final KnowledgeNodeRepository nodeRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public KnowledgeNode create(
            Long userId,
            NodeType nodeType,
            String title,
            String description
    ) {
        return nodeRepository.save(
                KnowledgeNode.create(userId, nodeType, title, description)
        );
    }

    /**
     * 같은 타입 노드의 이름만 모아 준다. 프롬프트에 넘길 참고 목록처럼 노드 자체가
     * 필요 없는 자리에서 쓴다.
     */
    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public List<String> findTitles(Long userId, NodeType nodeType) {
        return nodeRepository.findAllByUserIdAndNodeType(userId, nodeType).stream()
                .map(KnowledgeNode::getTitle)
                .toList();
    }

    /** 관계에서 얻은 id를 이름으로 바꿀 때 쓴다. 없는 id는 결과에서 빠진다. */
    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public List<KnowledgeNode> findAllByIds(Collection<UUID> ids) {
        return nodeRepository.findAllByIds(ids);
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public Optional<KnowledgeNode> findSubjectByNormalizedTitle(
            Long userId,
            String normalizedTitle
    ) {
        return nodeRepository.findByUserIdAndNodeTypeAndNormalizedTitle(
                userId, NodeType.SUBJECT, normalizedTitle
        );
    }

    /** 행은 남기고 조회에서만 뺀다. 관계는 지우지 않는다. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void delete(KnowledgeNode node) {
        node.delete();
        nodeRepository.save(node);
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public KnowledgeNode getOwned(UUID id, Long userId) {
        return nodeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND));
    }

}
