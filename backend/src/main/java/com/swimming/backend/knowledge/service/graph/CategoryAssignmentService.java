package com.swimming.backend.knowledge.service.graph;

import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.knowledge.domain.NodeTitleNormalizer;
import com.swimming.backend.knowledge.domain.SourceProcessingStatus;
import com.swimming.backend.knowledge.dto.in.NodeRef;
import com.swimming.backend.knowledge.dto.out.CategoryAssignmentDecision;
import com.swimming.backend.knowledge.dto.out.CategoryAssignmentInput;
import com.swimming.backend.knowledge.dto.out.SourceDigestResult;
import com.swimming.backend.knowledge.service.data.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.llm.CategoryAssignmentDecider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

/** 외부 판정과 저장을 조율한다. 모델 호출 동안 트랜잭션을 열지 않는다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryAssignmentService {
    static final int MIN_SOURCE_COUNT = 6;

    private final FolderService folderService;
    private final KnowledgeNodeService nodeService;
    private final CategoryAssignmentDecider decider;
    private final SourceGraphWriter graphWriter;

    public List<NodeRef> candidates(Long userId, Long folderId) {
        return nodeService.findCategoriesInFolder(userId, folderId).stream().map(NodeRef::from).toList();
    }

    public void assign(KnowledgeSource source, SourceDigestResult digest) {
        if (source.getProcessingStatus() != SourceProcessingStatus.COMPLETED) return;
        try {
            if (folderService.getSourceCount(source.getUserId(), source.getFolderId()) < MIN_SOURCE_COUNT) return;
            var categories = candidates(source.getUserId(), source.getFolderId());
            if (categories.isEmpty()) return;
            // 제안 이름이 비어도 요약으로 기존 Category 재사용은 판정한다. 새로 만들지만 못한다.
            var input = new CategoryAssignmentInput(source.getNode().getTitle(), digest.summary(),
                    digest.category(), categories);
            var sameTitle = sameTitle(digest.category(), categories);
            CategoryAssignmentDecision decision = sameTitle.isPresent()
                    ? new CategoryAssignmentDecision.Reuse(sameTitle.get().nodeId())
                    : decider.decide(input);
            if (!graphWriter.createCategoryAssignmentInTransaction(source, decision)) {
                log.info("[category-assignment] sourceId={} not assigned decision={}", source.getId(), decision);
            }
        } catch (RuntimeException e) {
            // 배정 오류로 소화 완료 결과를 버리지 않는다. 요약·원문·키는 로그에 넣지 않는다.
            log.warn("[category-assignment] failed sourceId={} errorType={}", source.getId(), e.getClass().getSimpleName());
        }
    }

    /**
     * 제안 이름이 기존 Category와 정규화 기준으로 같으면 판정할 것이 없다. 모델을 부르지 않는다.
     *
     * <p>판정기가 아니라 여기서 한다. 어떤 판정기를 쓰든 같은 이름을 다른 묶음으로 보내면 안 된다.
     */
    private Optional<NodeRef> sameTitle(String proposedTitle, List<NodeRef> categories) {
        String normalized = NodeTitleNormalizer.normalize(proposedTitle);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return categories.stream()
                .filter(category -> NodeTitleNormalizer.normalize(category.title()).equals(normalized))
                .findFirst();
    }
}
