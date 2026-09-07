package com.swimming.backend.knowledge.controller;

import com.swimming.backend.common.dto.CursorPage;
import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.knowledge.domain.SourceSearchOperator;
import com.swimming.backend.knowledge.dto.in.SourceDeleteResponse;
import com.swimming.backend.knowledge.dto.in.SourceDetailResponse;
import com.swimming.backend.knowledge.dto.in.SourceResponse;
import com.swimming.backend.knowledge.usecase.SourceCollectUseCase;
import com.swimming.backend.knowledge.usecase.SourceDeleteUseCase;
import com.swimming.backend.knowledge.usecase.SourceQueryUseCase;
import com.swimming.backend.knowledge.usecase.SourceReadUseCase;
import com.swimming.backend.knowledge.usecase.SourceSearchUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 저장한 링크 하나를 다룬다. 개념·목적으로 걸러 보는 검색도 여기 있다.
 *
 * <p>Folder에 매이지 않는 경로만 모은다. 링크는 저장한 뒤 Folder를 몰라도 열고 지울 수 있고,
 * 검색은 애초에 Folder를 넘나든다.
 */
@Tag(name = "지식 링크", description = "링크 검색(`/knowledge/sources`)과 상세·삭제·읽음·재소화(`/knowledge/sources/{sourceId}`).")
@RestController
@RequestMapping("/api/knowledge/sources")
@RequiredArgsConstructor
public class KnowledgeSourceController {

    private final SourceSearchUseCase searchUseCase;
    private final SourceQueryUseCase queryUseCase;
    private final SourceCollectUseCase commandUseCase;
    private final SourceDeleteUseCase deleteUseCase;
    private final SourceReadUseCase readUseCase;

    @GetMapping
    public ResponseEntity<CursorPage<SourceResponse>> search(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) List<UUID> subjectIds,
            @RequestParam(required = false) UUID topicId,
            @RequestParam(defaultValue = "AND") SourceSearchOperator operator,
            @RequestParam(required = false) Long folderId,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor
    ) {
        return ResponseEntity.ok(searchUseCase.search(
                authUser.id(), subjectIds, topicId, operator, folderId,
                CursorPage.validateSize(size), cursor
        ));
    }

    @GetMapping("/{sourceId}")
    public ResponseEntity<SourceDetailResponse> getSource(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable UUID sourceId
    ) {
        return ResponseEntity.ok(queryUseCase.get(authUser.id(), sourceId));
    }

    /** 딸린 목적도 함께 사라진다. 개념은 다른 문서가 쓰므로 남는다. */
    @DeleteMapping("/{sourceId}")
    public ResponseEntity<SourceDeleteResponse> deleteSource(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable UUID sourceId
    ) {
        return ResponseEntity.ok(deleteUseCase.delete(authUser.id(), sourceId));
    }

    /**
     * 읽음으로 표시한다. 읽은 시각은 서버가 정하므로 요청 본문이 없다.
     *
     * <p>토글이 아니라 상태를 지정하는 것이라, 같은 요청이 겹쳐 와도 결과가 같다.
     */
    @PostMapping("/{sourceId}/read")
    public ResponseEntity<Void> markRead(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable UUID sourceId
    ) {
        readUseCase.markRead(authUser.id(), sourceId);

        return ResponseEntity.noContent().build();
    }

    /** 읽음 표시를 되돌린다. */
    @DeleteMapping("/{sourceId}/read")
    public ResponseEntity<Void> markUnread(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable UUID sourceId
    ) {
        readUseCase.markUnread(authUser.id(), sourceId);

        return ResponseEntity.noContent().build();
    }

    /** 저장된 본문으로 LLM 소화만 다시 실행한다. */
    @PostMapping("/{sourceId}/retry")
    public ResponseEntity<SourceResponse> retrySource(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable UUID sourceId
    ) {
        return ResponseEntity.ok(commandUseCase.retry(authUser.id(), sourceId));
    }
}
