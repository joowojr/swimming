package com.swimming.backend.knowledge.service;

import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.RelationOrigin;
import com.swimming.backend.knowledge.dto.out.ResolvedNode;
import com.swimming.backend.knowledge.dto.out.SourceDigestResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    private final NodeResolver nodeResolver;
    private final KnowledgeRelationService relationService;

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
    public void write(KnowledgeSource source, SourceDigestResult result) {
        Long userId = source.getUserId();
        KnowledgeNode sourceNode = source.getNode();

        List<KnowledgeNode> subjects = nodeResolver
                .resolveSubjects(userId, result.subjects())
                .stream()
                .map(ResolvedNode::node)
                .toList();

        relationService.connectAll(sourceNode, subjects, RelationOrigin.AI);

        KnowledgeNode topic = nodeService.create(userId, NodeType.TOPIC, result.topic(), null);

        relationService.connect(sourceNode, topic, RelationOrigin.AI);
        relationService.connectAll(topic, subjects, RelationOrigin.AI);
    }
}
