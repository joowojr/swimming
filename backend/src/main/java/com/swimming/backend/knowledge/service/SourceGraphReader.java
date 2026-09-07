package com.swimming.backend.knowledge.service;

import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeRelation;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.RelationType;
import com.swimming.backend.knowledge.dto.in.NodeRef;
import com.swimming.backend.knowledge.dto.in.SourceConcepts;
import com.swimming.backend.knowledge.repository.KnowledgeNodeRepository;
import com.swimming.backend.knowledge.repository.KnowledgeRelationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Source에 걸린 개념과 목적을 읽는다.
 *
 * <p>Source를 통째로 받는 이유는 조회 횟수를 Source 개수와 무관하게 고정하기 위해서다.
 * 하나씩 읽으면 목록 20건에 관계 조회가 20번 나간다. 몇 건이든 관계 한 번, 제목 한 번이다.
 * {@link #read}가 {@link #readAll}을 그대로 부르는 것도 읽는 경로를 두 벌로 두지 않기
 * 위해서다.
 */
@Service
@RequiredArgsConstructor
public class SourceGraphReader {

    /** Source에서 나가는 관계. INVOLVES는 Topic Detail의 것이라 여기서 읽지 않는다. */
    private static final List<RelationType> FROM_SOURCE =
            List.of(RelationType.ABOUT, RelationType.SUPPORTS);

    private final KnowledgeRelationRepository relationRepository;
    private final KnowledgeNodeRepository nodeRepository;

    /** @return Source마다 하나씩. 걸린 것이 없으면 빈 값이 들어간다 */
    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public Map<UUID, SourceConcepts> readAll(List<KnowledgeSource> sources) {
        Map<UUID, SourceConcepts> concepts = new LinkedHashMap<>();

        if (sources.isEmpty()) {
            return concepts;
        }

        List<UUID> sourceIds = sources.stream().map(KnowledgeSource::getId).toList();
        List<KnowledgeRelation> relations =
                relationRepository.findAllByFromNodeIdIn(sourceIds, FROM_SOURCE);

        Map<UUID, String> titles = titlesOf(relations);

        Map<UUID, List<NodeRef>> subjects = new HashMap<>();
        Map<UUID, NodeRef> topics = new HashMap<>();

        for (KnowledgeRelation relation : relations) {
            String title = titles.get(relation.getToNodeId());

            // 관계는 있는데 노드가 없으면 그릴 이름이 없다. 조용히 뺀다.
            if (title == null) {
                continue;
            }

            NodeRef ref = new NodeRef(relation.getToNodeId(), title);

            if (relation.getRelationType() == RelationType.SUPPORTS) {
                topics.put(relation.getFromNodeId(), ref);
            } else {
                subjects.computeIfAbsent(relation.getFromNodeId(), key -> new ArrayList<>()).add(ref);
            }
        }

        for (UUID sourceId : sourceIds) {
            concepts.put(sourceId, new SourceConcepts(
                    topics.get(sourceId),
                    subjects.getOrDefault(sourceId, List.of())
            ));
        }

        return concepts;
    }

    /** 저장 직후처럼 대상이 하나뿐일 때 쓴다. */
    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public SourceConcepts read(KnowledgeSource source) {
        return readAll(List.of(source)).get(source.getId());
    }

    private Map<UUID, String> titlesOf(List<KnowledgeRelation> relations) {
        List<UUID> nodeIds = relations.stream().map(KnowledgeRelation::getToNodeId).distinct().toList();

        Map<UUID, String> titles = new LinkedHashMap<>();
        for (KnowledgeNode node : nodeRepository.findAllByIds(nodeIds)) {
            titles.put(node.getId(), node.getTitle());
        }

        return titles;
    }
}
