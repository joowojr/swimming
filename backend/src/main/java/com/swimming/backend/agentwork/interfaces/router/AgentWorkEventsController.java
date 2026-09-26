package com.swimming.backend.agentwork.interfaces.router;

import com.swimming.backend.agentwork.application.service.AgentWorkSseService;
import com.swimming.backend.common.security.AuthUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Cowork Board 실시간 갱신", description = "Cowork Board 상태 변경을 SSE로 전달한다.")
@RestController
@RequestMapping("/api/agent-work")
@RequiredArgsConstructor
public class AgentWorkEventsController {

    private final AgentWorkSseService agentWorkSseService;

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@AuthenticationPrincipal AuthUser authUser) {
        return agentWorkSseService.connect(authUser.id());
    }
}
