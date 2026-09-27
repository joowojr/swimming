package com.swimming.backend.agentwork.interfaces.router;

import com.swimming.backend.agentwork.interfaces.router.dto.CreateAgentAccessTokenRequest;
import com.swimming.backend.agentwork.application.dto.AgentAccessTokenResponse;
import com.swimming.backend.agentwork.application.dto.IssuedAgentAccessTokenResponse;
import com.swimming.backend.agentwork.application.usecase.AgentAccessTokenUseCase;
import com.swimming.backend.common.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/agent-work/tokens")
@RequiredArgsConstructor
public class AgentAccessTokenController {
    private final AgentAccessTokenUseCase useCase;

    @PostMapping
    public ResponseEntity<IssuedAgentAccessTokenResponse> issue(@AuthenticationPrincipal AuthUser authUser, @Valid @RequestBody CreateAgentAccessTokenRequest request) {
        IssuedAgentAccessTokenResponse response = useCase.issue(authUser.id(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public ResponseEntity<List<AgentAccessTokenResponse>> findAll(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(useCase.findAll(authUser.id()));
    }

    @DeleteMapping("/{tokenId}")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long tokenId) {
        useCase.revoke(authUser.id(), tokenId);
        return ResponseEntity.noContent().build();
    }
}
