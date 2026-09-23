package com.swimming.backend.agentwork.interfaces.router.dto;

import com.swimming.backend.agentwork.domain.AgentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
public record StartAgentWorkRequest(
        @NotEmpty @Size(max = 100) List<@NotNull @Valid AddWorkItemRequest> workItems,
        @NotNull AgentType agentType,
        @Size(max = 2000) String instruction
) {
}
