package com.swimming.backend.knowledge.controller;

import com.swimming.backend.common.dto.CursorPage;
import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.knowledge.dto.in.GraphResponse;
import com.swimming.backend.knowledge.usecase.KnowledgeGraphUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "지식 그래프", description = "Graph Browser의 진입(`/knowledge/graph`)과 노드 확장(`/knowledge/nodes/{nodeId}/graph`).")
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeGraphController {

    private final KnowledgeGraphUseCase graphUseCase;

    /** Folder로 진입한 초기 서브그래프. {@code limit}은 목록과 같은 상한을 쓴다. */
    @GetMapping("/graph")
    public ResponseEntity<GraphResponse> getFolderGraph(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam Long folderId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(
                graphUseCase.ofFolder(authUser.id(), folderId, CursorPage.validateSize(limit))
        );
    }

    /** 노드를 눌렀을 때의 1-hop, {@code 더 보기}의 2-hop. */
    @GetMapping("/nodes/{nodeId}/graph")
    public ResponseEntity<GraphResponse> getNodeGraph(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable UUID nodeId,
            @RequestParam(defaultValue = "1") int depth
    ) {
        return ResponseEntity.ok(graphUseCase.ofNode(authUser.id(), nodeId, depth));
    }
}
