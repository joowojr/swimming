package com.swimming.backend.agentwork.dto.in;

import com.swimming.backend.agentwork.domain.WorkResourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public record AddWorkItemRequest(
        @NotNull WorkResourceType resourceType,
        @NotBlank @Size(max = 100) String resourceId
) {
}
