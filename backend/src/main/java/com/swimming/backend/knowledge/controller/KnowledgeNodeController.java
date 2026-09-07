package com.swimming.backend.knowledge.controller;

import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.knowledge.dto.in.NodeDetailResponse;
import com.swimming.backend.knowledge.usecase.NodeUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 개념(Subject)과 목적(Topic) 노드를 펼쳐 보는 화면.
 *
 * <p>Source 저장·목록은 Folder 아래에 있지만, 여기서 다루는 노드는 Folder에 매이지 않고
 * 사용자 전역에서 재사용되므로 {@code /api/knowledge} 아래 평평하게 둔다.
 *
 * <p>{@code SOURCE} 노드는 {@link KnowledgeSourceController}가 맡으므로 여기서는 404다.
 */
@Tag(name = "지식 노드", description = "개념·목적 상세(`/knowledge/nodes/{nodeId}`)와 개념 삭제.")
@RestController
@RequestMapping("/api/knowledge/nodes")
@RequiredArgsConstructor
public class KnowledgeNodeController {

    private final NodeUseCase nodeUseCase;

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
