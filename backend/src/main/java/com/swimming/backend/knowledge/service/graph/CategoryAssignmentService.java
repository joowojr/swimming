package com.swimming.backend.knowledge.service.graph;

import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.SourceProcessingStatus;
import com.swimming.backend.knowledge.dto.in.NodeRef;
import com.swimming.backend.knowledge.dto.out.CategoryAssignmentInput;
import com.swimming.backend.knowledge.dto.out.SourceDigestResult;
import com.swimming.backend.knowledge.service.data.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.llm.CategoryAssignmentDecider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;

/** 외부 판정과 저장을 조율한다. 모델 호출 동안 트랜잭션을 열지 않는다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryAssignmentService {
    private final KnowledgeNodeService nodeService;
    private final CategoryAssignmentDecider decider;
    private final SourceGraphWriter graphWriter;

    public List<NodeRef> candidates(Long userId, Long folderId) {
        return nodeService.findCategoriesInFolder(userId, folderId).stream().map(NodeRef::from).toList();
    }

    public void assign(KnowledgeSource source, SourceDigestResult digest) {
        if (source.getProcessingStatus() != SourceProcessingStatus.COMPLETED) return;
        try {
            var categories = candidates(source.getUserId(), source.getFolderId());
            if (categories.isEmpty()) return;
            // 제안 이름이 비어도 요약으로 기존 Category 재사용은 판정한다. 새로 만들지만 못한다.
            var input = new CategoryAssignmentInput(source.getNode().getTitle(), digest.summary(),
                    digest.category(), categories);
            var decision = decider.decide(input);
            if (!graphWriter.writeCategory(source, decision)) {
                log.info("[category-assignment] sourceId={} not assigned decision={}", source.getId(), decision);
            }
        } catch (RuntimeException e) {
            // 배정 오류로 소화 완료 결과를 버리지 않는다. 요약·원문·키는 로그에 넣지 않는다.
            log.warn("[category-assignment] failed sourceId={} errorType={}", source.getId(), e.getClass().getSimpleName());
        }
    }
}
