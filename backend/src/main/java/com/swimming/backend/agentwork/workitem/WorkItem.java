package com.swimming.backend.agentwork.workitem;

import com.swimming.backend.agentwork.domain.WorkResourceType;

import java.time.Instant;
import lombok.Builder;

/** 외부 업무 리소스의 조회 모델. JPA Entity를 경계 밖으로 전달하지 않는다. */
@Builder
public record WorkItem(WorkResourceType type, String id, String title, Long containerId,
                       String containerName, Integer status, boolean important, boolean urgent, Instant createdAt) {}
