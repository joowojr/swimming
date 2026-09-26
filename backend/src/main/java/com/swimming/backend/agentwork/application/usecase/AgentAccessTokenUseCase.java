package com.swimming.backend.agentwork.application.usecase;

import com.swimming.backend.agentwork.interfaces.router.dto.CreateAgentAccessTokenRequest;
import com.swimming.backend.agentwork.application.dto.AgentAccessTokenResponse;
import com.swimming.backend.agentwork.application.dto.IssuedAgentAccessTokenResponse;
import com.swimming.backend.agentwork.application.service.AgentAccessTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentAccessTokenUseCase {
    private final AgentAccessTokenService service;
    public IssuedAgentAccessTokenResponse issue(Long userId, CreateAgentAccessTokenRequest request) { return service.issue(userId, request); }
    public List<AgentAccessTokenResponse> findAll(Long userId) { return service.findAll(userId); }
    public void revoke(Long userId, Long tokenId) { service.revoke(userId, tokenId); }
}
