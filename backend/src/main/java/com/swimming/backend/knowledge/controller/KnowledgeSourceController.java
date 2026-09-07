package com.swimming.backend.knowledge.controller;

import com.swimming.backend.common.dto.CursorPage;
import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.knowledge.domain.SourceProcessingStatus;
import com.swimming.backend.knowledge.dto.in.SourceCollectRequest;
import com.swimming.backend.knowledge.dto.in.SourceCollectResponse;
import com.swimming.backend.knowledge.dto.in.SourceResponse;
import com.swimming.backend.knowledge.usecase.SourceCollectUseCase;
import com.swimming.backend.knowledge.usecase.SourceListUseCase;
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

@RestController
@RequestMapping("/api/folders/{folderId}/knowledge/sources")
@RequiredArgsConstructor
public class KnowledgeSourceController {

    private final SourceCollectUseCase collectUseCase;
    private final SourceListUseCase listUseCase;

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
                collectUseCase.collect(authUser.id(), folderId, request)
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
                listUseCase.list(authUser.id(), folderId, status, CursorPage.validateSize(size), cursor)
        );
    }
}
