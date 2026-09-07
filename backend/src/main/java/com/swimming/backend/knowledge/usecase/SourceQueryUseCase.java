package com.swimming.backend.knowledge.usecase;

import com.swimming.backend.common.dto.CursorPage;
import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.SourceProcessingStatus;
import com.swimming.backend.knowledge.dto.in.SourceConcepts;
import com.swimming.backend.knowledge.dto.in.SourceDetailResponse;
import com.swimming.backend.knowledge.dto.in.SourceResponse;
import com.swimming.backend.knowledge.repository.SourcePageQuery;
import com.swimming.backend.knowledge.service.SourceGraphReader;
import com.swimming.backend.knowledge.service.SourceCursorCodec;
import com.swimming.backend.knowledge.service.data.KnowledgeSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 저장한 Source를 읽는다. 목록과 상세가 여기 함께 있다.
 *
 * <p>개념과 목적은 두 경로가 {@link SourceGraphReader} 하나를 같이 쓴다. Source에 붙은
 * 관계를 읽는 규칙이 두 벌로 갈라지면 상세와 목록이 다른 개념을 보여 줄 수 있다.
 */
@Service
@RequiredArgsConstructor
public class SourceQueryUseCase {

    private final FolderService folderService;
    private final KnowledgeSourceService sourceService;
    private final SourceGraphReader conceptReader;

    /**
     * Folder에 모은 Source를 최근 순으로 보여준다.
     *
     * <p>소화를 비동기로 옮기면 이 API가 진행 상황을 확인하는 자리가 된다. 그래서 아직
     * 소화되지 않은 Source도 상태만 담아 그대로 내려준다.
     */
    public CursorPage<SourceResponse> list(
            Long userId,
            Long folderId,
            SourceProcessingStatus status,
            int size,
            String cursor
    ) {
        folderService.validateOwnership(userId, folderId);

        SourceCursorCodec.Decoded decoded =
                StringUtils.hasText(cursor) ? SourceCursorCodec.decode(cursor) : null;

        // 한 건 더 읽어 다음 페이지가 있는지 본다. 총 개수를 세지 않아도 된다.
        List<KnowledgeSource> fetched = sourceService.findPage(new SourcePageQuery(
                userId,
                folderId,
                status,
                size + 1,
                decoded == null ? null : decoded.createdAt(),
                decoded == null ? null : decoded.nodeId()
        ));

        boolean hasNext = fetched.size() > size;
        List<KnowledgeSource> page = hasNext ? fetched.subList(0, size) : fetched;

        // 개념은 목록을 통째로 넘겨 한 번에 읽는다. 하나씩 읽으면 관계 조회가 그만큼 나간다.
        Map<UUID, SourceConcepts> concepts = conceptReader.readAll(page);

        return new CursorPage<>(
                page.stream()
                        .map(source -> SourceResponse.of(source, concepts.get(source.getId())))
                        .toList(),
                hasNext ? nextCursor(page.getLast()) : null,
                hasNext
        );
    }

    /** Source 하나를 원문 정보까지 붙여 돌려준다. */
    public SourceDetailResponse get(Long userId, UUID sourceId) {
        KnowledgeSource source = sourceService.getOwned(sourceId, userId);

        return SourceDetailResponse.of(source, conceptReader.read(source));
    }

    private String nextCursor(KnowledgeSource last) {
        return SourceCursorCodec.encode(last.getNode().getCreatedAt(), last.getId());
    }
}
