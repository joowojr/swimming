package com.swimming.backend.knowledge.usecase;

import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeRelation;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.RelationType;
import com.swimming.backend.knowledge.service.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.KnowledgeRelationService;
import com.swimming.backend.knowledge.service.KnowledgeSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 저장한 링크를 지운다.
 *
 * <p>행은 남기고 노드만 지운 것으로 표시한다. 관계도 지우지 않는다. 조회가 지운 노드를
 * 거르므로 그 노드에 걸린 간선은 저절로 빠진다.
 */
@Service
@RequiredArgsConstructor
public class SourceDeleteUseCase {

    private static final List<RelationType> TO_TOPIC = List.of(RelationType.SUPPORTS);

    private final KnowledgeSourceService sourceService;
    private final KnowledgeNodeService nodeService;
    private final KnowledgeRelationService relationService;
    private final FolderService folderService;

    /**
     * 딸린 Topic도 함께 지운다.
     *
     * <p>Topic은 Source마다 새로 만들고 재사용 판정을 하지 않는다(정본 §8). 문서를 지우고
     * Topic만 남기면 문서 0개짜리 Topic이 남는데, `sources`가 항상 하나라는 계약이 거기서
     * 깨진다.
     *
     * <p>Subject는 남긴다. 여러 문서가 공유하는 개념이고, 지웠다가 같은 개념을 다시 저장하면
     * 재사용이 끊겨 노드가 갈라진다.
     *
     * <p>마지막 링크였다면 폴더의 표시를 끈다. 켜진 채로 두면 지울 수 있는 폴더를 막는다.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void delete(Long userId, UUID sourceId) {
        KnowledgeSource source = sourceService.getOwned(sourceId, userId);

        for (KnowledgeNode topic : topicsOf(sourceId)) {
            nodeService.delete(topic);
        }

        sourceService.delete(source);

        Long folderId = source.getFolderId();
        folderService.updateHasSource(userId, folderId, sourceService.existsInFolder(userId, folderId));
    }

    private List<KnowledgeNode> topicsOf(UUID sourceId) {
        List<UUID> topicIds = relationService.findOutgoing(List.of(sourceId), TO_TOPIC).stream()
                .map(KnowledgeRelation::getToNodeId)
                .toList();

        return nodeService.findAllByIds(topicIds);
    }
}
