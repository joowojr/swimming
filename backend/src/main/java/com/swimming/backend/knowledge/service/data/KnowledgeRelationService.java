package com.swimming.backend.knowledge.service.data;

import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeRelation;
import com.swimming.backend.knowledge.domain.RelationOrigin;
import com.swimming.backend.knowledge.domain.RelationType;
import com.swimming.backend.knowledge.repository.KnowledgeRelationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 관계 타입은 양끝 노드 타입에서 나온다.
 *
 * <p>Source에서 Subject로 잇는 관계는 {@code ABOUT} 하나뿐이므로 호출하는 쪽이 관계 이름을
 * 따로 넘기지 않는다. 이름을 넘기게 하면 {@code INVOLVES}에 Source를 넣는 것 같은 조합이
 * 실행할 때까지 드러나지 않는다.
 */
@Service
@RequiredArgsConstructor
public class KnowledgeRelationService {

    private final KnowledgeRelationRepository relationRepository;

    /**
     * 같은 관계가 이미 있으면 근거만 갱신한다.
     *
     * <p>여러 문서가 같은 개념을 다루면 같은 관계를 반복해서 관찰하게 된다. 그때마다 행을
     * 늘리지 않고 마지막 관찰로 덮는다. 사용자가 만든 관계는 저장소가 기존 행에 관찰을
     * 반영할 때 걸러낸다.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public KnowledgeRelation connect(KnowledgeNode from, KnowledgeNode to, RelationOrigin origin) {
        return connectAll(from, List.of(to), origin).getFirst();
    }

    /**
     * 한 노드에서 같은 타입의 여러 노드로 잇는다. Source → 여러 Subject처럼 쓴다.
     *
     * <p>대상마다 저장을 부르지 않고 한 번에 넘긴다. Subject 수만큼 왕복하던 것을 묶음당
     * 조회 한 번과 저장 한 번으로 줄인다.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public List<KnowledgeRelation> connectAll(
            KnowledgeNode from,
            List<KnowledgeNode> targets,
            RelationOrigin origin
    ) {
        if (targets.isEmpty()) {
            return List.of();
        }

        return relationRepository.saveAll(targets.stream()
                .map(to -> KnowledgeRelation.create(
                        from,
                        to,
                        RelationType.between(from.getNodeType(), to.getNodeType()),
                        origin,
                        null,
                        null
                ))
                .toList());
    }

    /**
     * 이 노드들에서 나가는 같은 타입의 관계를 모두 끊는다.
     *
     * <p>노드를 지우는 것만으로는 간선이 남는다. 조회가 지운 노드를 걸러 주므로 화면에는
     * 드러나지 않지만, 사라진 묶음의 간선이 행으로 남아 있을 이유가 없다.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public int disconnectAllFrom(Collection<UUID> fromNodeIds, RelationType relationType) {
        return relationRepository.deleteAllFrom(fromNodeIds, relationType);
    }

    /** 소유권과 현재 소속을 확인한 문서 한 건의 카테고리 연결을 끊는다. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void disconnect(UUID fromNodeId, UUID toNodeId, RelationType relationType) {
        relationRepository.delete(fromNodeId, toNodeId, relationType);
    }

    /** 이 노드들에서 나가는 관계. Source가 다루는 개념처럼 정방향으로 읽는다. */
    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public List<KnowledgeRelation> findOutgoing(
            Collection<UUID> fromNodeIds,
            Collection<RelationType> relationTypes
    ) {
        return relationRepository.findAllByFromNodeIdIn(fromNodeIds, relationTypes);
    }

    /** 이 노드들로 들어오는 관계. 개념을 다루는 문서처럼 역방향으로 읽는다. */
    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public List<KnowledgeRelation> findIncoming(
            Collection<UUID> toNodeIds,
            Collection<RelationType> relationTypes
    ) {
        return relationRepository.findAllByToNodeIdIn(toNodeIds, relationTypes);
    }
}
