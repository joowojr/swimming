package com.swimming.backend.agentwork.interfaces.router;

import com.swimming.backend.agentwork.interfaces.router.dto.AddWorkItemRequest;
import com.swimming.backend.agentwork.domain.BoardSort;
import com.swimming.backend.agentwork.application.dto.AddWorkItemResult;
import com.swimming.backend.agentwork.application.dto.AgentBoardResponse;
import com.swimming.backend.agentwork.application.dto.AgentSessionEventResponse;
import com.swimming.backend.agentwork.application.dto.AgentWorkItemResponse;
import com.swimming.backend.agentwork.application.usecase.AgentBoardUseCase;
import com.swimming.backend.common.security.AuthUser;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Tag(name = "Cowork Board", description = "Cowork Board(`/agent-board`)의 Lane 조회와 보드에 할 일 올리기.")
@RestController
@RequestMapping("/api/agent-work")
@RequiredArgsConstructor
public class AgentBoardController {

    private final AgentBoardUseCase agentBoardUseCase;
    private final com.swimming.backend.agentwork.application.usecase.AgentWorkItemUseCase agentWorkItemUseCase;

    @GetMapping("/board")
    public ResponseEntity<AgentBoardResponse> getBoard(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(defaultValue = "PRIORITY") BoardSort sort
    ) {
        return ResponseEntity.ok(agentBoardUseCase.getBoard(authUser.id(), sort));
    }

    /** 기존 할 일을 보드로 가져온다. 이미 보드에 있으면 200으로 그 카드를 돌려준다. */
    @PostMapping("/work-items")
    public ResponseEntity<AgentWorkItemResponse> addWorkItem(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody AddWorkItemRequest request
    ) {
        AddWorkItemResult result = agentWorkItemUseCase.addWorkItem(authUser.id(), request);
        if (!result.created()) {
            return ResponseEntity.ok(result.workItem());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/agent-work/work-items/{id}")
                .buildAndExpand(result.workItem().id())
                .toUri();
        return ResponseEntity.created(location).body(result.workItem());
    }

    @GetMapping("/work-items/{workItemId}")
    public ResponseEntity<AgentWorkItemResponse> getWorkItem(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long workItemId
    ) {
        return ResponseEntity.ok(agentWorkItemUseCase.getWorkItem(authUser.id(), workItemId));
    }

    /** Agent 활동 Tab. 카드의 모든 세션 이벤트를 시간순으로 준다. */
    @GetMapping("/work-items/{workItemId}/events")
    public ResponseEntity<List<AgentSessionEventResponse>> getEvents(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long workItemId
    ) {
        return ResponseEntity.ok(agentWorkItemUseCase.getEvents(authUser.id(), workItemId));
    }

    @GetMapping("/sessions/{sessionId}/events")
    public ResponseEntity<List<AgentSessionEventResponse>> getSessionEvents(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long sessionId
    ) {
        return ResponseEntity.ok(agentBoardUseCase.getSessionEvents(authUser.id(), sessionId));
    }
}
