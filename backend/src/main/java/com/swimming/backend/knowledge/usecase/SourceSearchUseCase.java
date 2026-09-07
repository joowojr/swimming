package com.swimming.backend.knowledge.usecase;

import com.swimming.backend.common.dto.CursorPage;
import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.knowledge.domain.KnowledgeRelation;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.RelationType;
import com.swimming.backend.knowledge.domain.SourceSearchOperator;
import com.swimming.backend.knowledge.dto.in.SourceConcepts;
import com.swimming.backend.knowledge.dto.in.SourceResponse;
import com.swimming.backend.knowledge.repository.SourceSearchPageQuery;
import com.swimming.backend.knowledge.service.data.KnowledgeRelationService;
import com.swimming.backend.knowledge.service.data.KnowledgeSourceService;
import com.swimming.backend.knowledge.service.SourceGraphReader;
import com.swimming.backend.knowledge.service.SourceCursorCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 개념·목적 관계로 Source를 찾는다. 관계는 후보만 고르고 소유권은 Source 조회가 검증한다. */
@Service
@RequiredArgsConstructor
public class SourceSearchUseCase {

    private final FolderService folderService;
    private final KnowledgeRelationService relationService;
    private final KnowledgeSourceService sourceService;
    private final SourceGraphReader conceptReader;

    public CursorPage<SourceResponse> search(
            Long userId,
            List<UUID> subjectIds,
            UUID topicId,
            SourceSearchOperator operator,
            Long folderId,
            int size,
            String cursor
    ) {
        if (folderId != null) {
            folderService.validateOwnership(userId, folderId);
        }

        Set<UUID> subjects = subjectIds == null
                ? Set.of()
                : new LinkedHashSet<>(subjectIds);
        Set<UUID> candidates = null;

        if (!subjects.isEmpty()) {
            candidates = sourcesMatchingSubjects(subjects, operator);
        }

        if (topicId != null) {
            Set<UUID> topicSources = sourceIds(relationService.findIncoming(
                    List.of(topicId), List.of(RelationType.SUPPORTS)
            ));
            candidates = intersect(candidates, topicSources);
        }

        SourceCursorCodec.Decoded decoded =
                StringUtils.hasText(cursor) ? SourceCursorCodec.decode(cursor) : null;
        List<KnowledgeSource> fetched = sourceService.findSearchPage(new SourceSearchPageQuery(
                userId,
                folderId,
                candidates,
                size + 1,
                decoded == null ? null : decoded.createdAt(),
                decoded == null ? null : decoded.nodeId()
        ));

        boolean hasNext = fetched.size() > size;
        List<KnowledgeSource> page = hasNext ? fetched.subList(0, size) : fetched;
        Map<UUID, SourceConcepts> concepts = conceptReader.readAll(page);

        return new CursorPage<>(
                page.stream()
                        .map(source -> SourceResponse.of(source, concepts.get(source.getId())))
                        .toList(),
                hasNext ? SourceCursorCodec.encode(
                        page.getLast().getNode().getCreatedAt(), page.getLast().getId()
                ) : null,
                hasNext
        );
    }

    private Set<UUID> sourcesMatchingSubjects(Set<UUID> subjectIds, SourceSearchOperator operator) {
        List<KnowledgeRelation> relations = relationService.findIncoming(
                subjectIds, List.of(RelationType.ABOUT)
        );

        if (operator == SourceSearchOperator.OR) {
            return sourceIds(relations);
        }

        Map<UUID, Set<UUID>> subjectsBySource = new HashMap<>();
        for (KnowledgeRelation relation : relations) {
            subjectsBySource
                    .computeIfAbsent(relation.getFromNodeId(), ignored -> new LinkedHashSet<>())
                    .add(relation.getToNodeId());
        }

        Set<UUID> matches = new LinkedHashSet<>();
        subjectsBySource.forEach((sourceId, matchedSubjects) -> {
            if (matchedSubjects.containsAll(subjectIds)) {
                matches.add(sourceId);
            }
        });
        return matches;
    }

    private Set<UUID> sourceIds(List<KnowledgeRelation> relations) {
        Set<UUID> sourceIds = new LinkedHashSet<>();
        relations.forEach(relation -> sourceIds.add(relation.getFromNodeId()));
        return sourceIds;
    }

    private Set<UUID> intersect(Set<UUID> current, Set<UUID> additional) {
        if (current == null) {
            return new LinkedHashSet<>(additional);
        }
        current.retainAll(additional);
        return current;
    }
}
