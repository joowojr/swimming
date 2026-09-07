package com.swimming.backend.knowledge.service.graph;

import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.NodeTitleNormalizer;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.dto.out.ResolvedNode;
import com.swimming.backend.knowledge.repository.KnowledgeNodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM이 뽑아 온 이름을 실제 노드로 정한다. 문서마다 새 노드를 만들지 않기 위한 단계다.
 *
 * <p>Subject만 대상이다. Topic은 Source당 하나라 재사용 판정을 하지 않고 그때마다 만든다.
 */
@Service
@RequiredArgsConstructor
public class NodeResolver {

    private final KnowledgeNodeRepository nodeRepository;

    /**
     * 후보를 하나씩 기존 Subject에 맞춰 보고, 맞는 것이 없을 때만 새로 만든다.
     *
     * <p>비교는 title이 아니라 {@link NodeTitleNormalizer}를 거친 값으로 한다. LLM은 같은
     * 개념을 문서마다 {@code OIDC}, {@code oidc}, {@code AWS-OIDC}처럼 다르게 적어 내는데,
     * 글자 그대로 비교하면 그때마다 새 노드가 생긴다.
     *
     * <p>목록을 통째로 받는 이유는 한 응답 안에서도 같은 개념이 두 번 나오기 때문이다.
     * 저장된 노드와의 중복과 요청 안에서의 중복을 같은 자리에서 걷어낸다.
     *
     * @return 입력 순서를 지키되 중복이 제거된 결과. 이름이 비어 있는 후보는 건너뛴다.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public List<ResolvedNode> resolveSubjects(Long userId, List<String> candidates) {
        Map<String, ResolvedNode> resolved = new LinkedHashMap<>();

        for (String candidate : candidates) {
            String normalized = NodeTitleNormalizer.normalize(candidate);

            if (normalized.isEmpty() || resolved.containsKey(normalized)) {
                continue;
            }

            resolved.put(normalized, resolveOne(userId, candidate, normalized));
        }

        return List.copyOf(new ArrayList<>(resolved.values()));
    }

    private ResolvedNode resolveOne(Long userId, String candidate, String normalized) {
        return nodeRepository
                .findByUserIdAndNodeTypeAndNormalizedTitle(userId, NodeType.SUBJECT, normalized)
                .map(node -> ResolvedNode.exact(candidate, node))
                .orElseGet(() -> ResolvedNode.created(candidate, nodeRepository.save(
                        KnowledgeNode.create(userId, NodeType.SUBJECT, candidate.strip(), null)
                )));
    }
}
