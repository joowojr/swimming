package com.swimming.backend.knowledge.controller;

import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.knowledge.dto.in.NodeDetailResponse;
import com.swimming.backend.knowledge.dto.in.NodeRef;
import com.swimming.backend.knowledge.dto.in.NodeTitleUpdateRequest;
import com.swimming.backend.knowledge.dto.in.CategoryMergeRequest;
import com.swimming.backend.knowledge.usecase.NodeUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 개념(Subject)과 목적(Topic) 상세, 개념 삭제, Category·Topic 이름 수정.
 *
 * <p>Source 저장·목록은 Folder 아래에 있지만, 여기서 다루는 노드는 Folder에 매이지 않고
 * 사용자 전역에서 재사용되므로 {@code /api/knowledge} 아래 평평하게 둔다.
 *
 * <p>{@code SOURCE} 상세는 {@link KnowledgeSourceController}가 맡는다. 이름 수정은
 * Category·Topic만 허용하며 Source·Subject 요청은 400으로 거절한다. 중복 Category 이름은
 * 409로 거절하고, 문서 이동과 전체 병합은 별도 PUT API로 처리한다.
 */
@Tag(name = "지식 노드", description = "개념·목적 상세와 개념 삭제, 카테고리·목적 이름 수정.")
@RestController
@RequestMapping("/api/knowledge/nodes")
@RequiredArgsConstructor
public class KnowledgeNodeController {

    private final NodeUseCase nodeUseCase;

    /** Category·Topic 이름만 수정한다. 중복 Category 이름은 409로 거절한다. */
    @PatchMapping("/{nodeId}/title")
    public ResponseEntity<NodeRef> updateTitle(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable UUID nodeId,
            @Valid @RequestBody NodeTitleUpdateRequest request
    ) {
        return ResponseEntity.ok(nodeUseCase.updateTitle(authUser.id(), nodeId, request.title()));
    }

    /** 기존 Category로 문서를 옮기고 대상 노드 참조를 반환한다. */
    @PutMapping("/{nodeId}/merge")
    public ResponseEntity<NodeRef> merge(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable UUID nodeId,
            @Valid @RequestBody CategoryMergeRequest request
    ) {
        return ResponseEntity.ok(nodeUseCase.merge(authUser.id(), nodeId, request.targetCategoryId(), request.sourceId()));
    }

    /** Subject와 Topic을 함께 맡는다. */
    @GetMapping("/{nodeId}")
    public ResponseEntity<NodeDetailResponse> getNode(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable UUID nodeId
    ) {
        return ResponseEntity.ok(nodeUseCase.get(authUser.id(), nodeId));
    }

    /** 개념만 지운다. 그 개념을 다루던 문서는 그대로 남는다. */
    @DeleteMapping("/{nodeId}")
    public ResponseEntity<Void> deleteNode(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable UUID nodeId
    ) {
        nodeUseCase.delete(authUser.id(), nodeId);
        return ResponseEntity.noContent().build();
    }
}
