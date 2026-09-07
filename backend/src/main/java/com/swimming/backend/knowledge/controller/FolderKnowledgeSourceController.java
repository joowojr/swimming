package com.swimming.backend.knowledge.controller;

import com.swimming.backend.common.dto.CursorPage;
import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.knowledge.domain.SourceProcessingStatus;
import com.swimming.backend.knowledge.dto.in.SourceCollectRequest;
import com.swimming.backend.knowledge.dto.in.SourceCollectResponse;
import com.swimming.backend.knowledge.dto.in.SourceResponse;
import com.swimming.backend.knowledge.usecase.SourceCollectUseCase;
import com.swimming.backend.knowledge.usecase.SourceQueryUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Folder 안에서 링크를 모으고 본다.
 *
 * <p>저장과 목록만 Folder 아래에 둔다. 저장한 뒤의 상세·삭제·재소화는 Folder를 몰라도 되므로
 * {@link KnowledgeSourceController}가 맡는다.
 */
@Tag(name = "지식 링크 수집", description = "Folder에 링크를 저장(`POST`)하고 목록을 본다(`GET`).")
@RestController
@RequestMapping("/api/folders/{folderId}/knowledge/sources")
@RequiredArgsConstructor
public class FolderKnowledgeSourceController {

    private final SourceCollectUseCase commandUseCase;
    private final SourceQueryUseCase queryUseCase;

    /**
     * 201이 아니라 200이다. 한 요청이 여러 Source를 만들고 일부만 실패할 수 있어 대표
     * Location을 정할 수 없다. 링크마다의 결과는 본문에 담는다.
     */
    @PostMapping
    public ResponseEntity<SourceCollectResponse> collect(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long folderId,
            @Valid @RequestBody SourceCollectRequest request
    ) {
        return ResponseEntity.ok(
                commandUseCase.collect(authUser.id(), folderId, request)
        );
    }

    @GetMapping
    public ResponseEntity<CursorPage<SourceResponse>> getList(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long folderId,
            @RequestParam(required = false) SourceProcessingStatus status,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor
    ) {
        return ResponseEntity.ok(
                queryUseCase.list(authUser.id(), folderId, status, CursorPage.validateSize(size), cursor)
        );
    }
}
