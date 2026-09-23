package com.swimming.backend.agentwork.dto.out;

import com.swimming.backend.agentwork.domain.WorkResourceType;

/**
 * 출처와 무관한 Work Item 모양. Swimming Task는 폴더를 container로 담는다.
 * 미분류 할 일은 containerId·containerName이 null이다.
 */
public record WorkItemResponse(
        WorkResourceType type,
        String id,
        String title,
        Long containerId,
        String containerName,
        Integer status,
        boolean important,
        boolean urgent
) {
}
