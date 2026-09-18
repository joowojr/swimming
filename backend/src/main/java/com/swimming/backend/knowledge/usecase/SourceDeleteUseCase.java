package com.swimming.backend.knowledge.usecase;

import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.folder.domain.Folder;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeRelation;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.RelationType;
import com.swimming.backend.knowledge.dto.in.SourceDeleteResponse;
import com.swimming.backend.knowledge.service.data.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.data.KnowledgeRelationService;
import com.swimming.backend.knowledge.service.data.KnowledgeSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 저장한 링크를 지운다.
 *
 * <p>행은 남기고 노드만 지운 것으로 표시한다. 관계도 지우지 않는다. 조회가 지운 노드를
 * 거르므로 그 노드에 걸린 간선은 저절로 빠진다.
 */
@Service
@RequiredArgsConstructor
public class SourceDeleteUseCase {

    /** Source에서 Topic으로 향하는 관계. 딸린 Topic을 찾을 때 쓴다. */
    private static final List<RelationType> TO_TOPIC = List.of(RelationType.SUPPORTS);
    private static final List<RelationType> TO_SUBJECT = List.of(RelationType.ABOUT);

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
     * <p>Subject는 다른 활성 Source가 계속 쓰는 경우만 남긴다. 마지막 Source가 삭제되면
     * 임베딩 검색 후보에 고아 Subject가 남지 않도록 함께 soft delete한다.
     *
     * <p>마지막 링크였다면 폴더의 표시를 끈다. 켜진 채로 두면 지울 수 있는 폴더를 막는다.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public SourceDeleteResponse delete(Long userId, UUID sourceId) {
        KnowledgeSource source = sourceService.getOwned(sourceId, userId);
        Folder folder = folderService.lockOwned(userId, source.getFolderId());
        source = sourceService.getOwned(sourceId, userId);
        List<UUID> subjectIds = subjectIdsOf(sourceId);

        nodeService.deleteAll(userId, topicIdsOf(sourceId));

        sourceService.delete(source);
        folder.decrementSourceCount();
        folderService.updateSourceCount(userId, folder.getId(), folder.getSourceCount());
        deleteOrphanSubjects(userId, subjectIds);

        Long folderId = source.getFolderId();
        boolean hasSource = folder.isHasSource();
        return new SourceDeleteResponse(folderId, hasSource);
    }

    private List<UUID> topicIdsOf(UUID sourceId) {
        return relationService.findOutgoing(List.of(sourceId), TO_TOPIC).stream()
                .map(KnowledgeRelation::getToNodeId)
                .distinct()
                .toList();
    }

    private List<UUID> subjectIdsOf(UUID sourceId) {
        return relationService.findOutgoing(List.of(sourceId), TO_SUBJECT).stream()
                .map(KnowledgeRelation::getToNodeId)
                .distinct()
                .toList();
    }

    private void deleteOrphanSubjects(Long userId, List<UUID> subjectIds) {
        if (subjectIds.isEmpty()) {
            return;
        }

        List<KnowledgeRelation> references = relationService.findIncoming(
                subjectIds, TO_SUBJECT);
        Set<UUID> activeSourceIds = sourceService.findAllByIds(
                        references.stream()
                                .map(KnowledgeRelation::getFromNodeId)
                                .distinct()
                                .toList()
                ).stream()
                .map(KnowledgeSource::getId)
                .collect(Collectors.toSet());
        Set<UUID> referencedSubjectIds = references.stream()
                .filter(reference -> activeSourceIds.contains(reference.getFromNodeId()))
                .map(KnowledgeRelation::getToNodeId)
                .collect(Collectors.toSet());

        nodeService.deleteAll(
                userId,
                subjectIds.stream()
                        .filter(subjectId -> !referencedSubjectIds.contains(subjectId))
                        .toList()
        );
    }
}
