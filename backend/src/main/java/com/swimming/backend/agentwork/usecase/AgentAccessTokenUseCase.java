package com.swimming.backend.agentwork.usecase;

import com.swimming.backend.agentwork.dto.in.CreateAgentAccessTokenRequest;
import com.swimming.backend.agentwork.dto.out.AgentAccessTokenResponse;
import com.swimming.backend.agentwork.dto.out.IssuedAgentAccessTokenResponse;
import com.swimming.backend.agentwork.service.AgentAccessTokenService;
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
