package com.swimming.backend.knowledge.controller;

import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.knowledge.dto.in.NodeDetailResponse;
import com.swimming.backend.knowledge.dto.in.SourceDetailResponse;
import com.swimming.backend.knowledge.usecase.NodeDeleteUseCase;
import com.swimming.backend.knowledge.usecase.NodeDetailUseCase;
import com.swimming.backend.knowledge.usecase.SourceDeleteUseCase;
import com.swimming.backend.knowledge.usecase.SourceDetailUseCase;
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
 * 노드 하나를 펼쳐 보는 화면.
 *
 * <p>Source 저장·목록은 Folder 아래에 있지만, 여기서 다루는 노드는 Folder에 매이지 않고
 * 사용자 전역에서 재사용되므로 {@code /api/knowledge} 아래 평평하게 둔다.
 */
@Tag(name = "지식 노드", description = "Source 상세(`/knowledge/sources/{sourceId}`)와 개념·목적 상세(`/knowledge/nodes/{nodeId}`).")
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeNodeController {

    private final SourceDetailUseCase sourceDetailUseCase;
    private final NodeDetailUseCase nodeDetailUseCase;
    private final SourceDeleteUseCase sourceDeleteUseCase;
    private final NodeDeleteUseCase nodeDeleteUseCase;

    @GetMapping("/sources/{sourceId}")
    public ResponseEntity<SourceDetailResponse> getSource(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable UUID sourceId
    ) {
        return ResponseEntity.ok(sourceDetailUseCase.get(authUser.id(), sourceId));
    }

    /** Subject와 Topic을 함께 맡는다. {@code SOURCE} 노드는 위 경로가 맡으므로 404다. */
    @GetMapping("/nodes/{nodeId}")
    public ResponseEntity<NodeDetailResponse> getNode(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable UUID nodeId
    ) {
        return ResponseEntity.ok(nodeDetailUseCase.get(authUser.id(), nodeId));
    }

    /** 딸린 목적도 함께 사라진다. 개념은 다른 문서가 쓰므로 남는다. */
    @DeleteMapping("/sources/{sourceId}")
    public ResponseEntity<Void> deleteSource(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable UUID sourceId
    ) {
        sourceDeleteUseCase.delete(authUser.id(), sourceId);
        return ResponseEntity.noContent().build();
    }

    /** 개념만 지운다. 그 개념을 다루던 문서는 그대로 남는다. */
    @DeleteMapping("/nodes/{nodeId}")
    public ResponseEntity<Void> deleteNode(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable UUID nodeId
    ) {
        nodeDeleteUseCase.delete(authUser.id(), nodeId);
        return ResponseEntity.noContent().build();
    }
}
