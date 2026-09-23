package com.swimming.backend.agentwork.workitem;

import com.swimming.backend.agentwork.domain.WorkResourceType;
import lombok.Builder;

@Builder
public record WorkItemId(WorkResourceType type, String id) {}
