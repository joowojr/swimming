package com.swimming.backend.agentwork.controller;

import com.swimming.backend.agentwork.dto.in.AgentReportRequest;
import com.swimming.backend.agentwork.dto.in.StartAgentWorkRequest;
import com.swimming.backend.agentwork.dto.out.AgentSessionResponse;
import com.swimming.backend.agentwork.dto.out.StartAgentWorkResult;
import com.swimming.backend.agentwork.usecase.AgentSessionUseCase;
import com.swimming.backend.agentwork.usecase.AgentSessionReportUseCase;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@Tag(name = "Agent 세션", description = "코드 에이전트의 작업 상태 보고. MCP Tool과 스크립트가 함께 쓴다.")
@RestController
@RequestMapping("/api/agent-work/sessions")
@RequiredArgsConstructor
public class AgentSessionController {

    private final AgentSessionUseCase agentSessionUseCase;
    private final AgentSessionReportUseCase agentSessionReportUseCase;

    /**
     * 여러 할 일을 하나의 세션으로 시작한다. 보드에 없는 할 일이면 보드에 자동으로 올린다.
     * 세션을 새로 만들면 201, 끝난 세션을 다시 열면 200이다. 진행 중인 세션이 있거나 서로 다른 기존 세션을 묶으면 409다.
     * 공유 세션을 다시 열면 요청에 없는 기존 연결 할 일에도 상태가 적용된다.
     */
    @PostMapping
    public ResponseEntity<AgentSessionResponse> start(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody StartAgentWorkRequest request
    ) {
        StartAgentWorkResult result = agentSessionReportUseCase.start(authUser.id(), request);
        if (!result.created()) {
            return ResponseEntity.ok(result.session());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/agent-work/sessions/{id}")
                .buildAndExpand(result.session().id())
                .toUri();
        return ResponseEntity.created(location).body(result.session());
    }

    @PostMapping("/{sessionId}/complete")
    public ResponseEntity<Void> complete(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long sessionId,
            @Valid @RequestBody AgentReportRequest request
    ) {
        agentSessionReportUseCase.complete(authUser.id(), sessionId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{sessionId}/fail")
    public ResponseEntity<Void> fail(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long sessionId,
            @Valid @RequestBody AgentReportRequest request
    ) {
        agentSessionReportUseCase.fail(authUser.id(), sessionId, request);
        return ResponseEntity.noContent().build();
    }
}
