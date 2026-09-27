package com.swimming.backend.agentwork.application.port;

import java.util.Collection;
import java.util.Map;
import java.util.List;

public interface WorkItemPort {
    /** 사용자가 소유한 삭제되지 않은 업무 리소스를 모두 반환한다. */
    List<WorkItem> readAllOwned(Long userId);

    /** 요청 식별자를 키로 반환한다. 타인·삭제·존재하지 않는 리소스는 결과에서 제외한다. */
    Map<WorkItemId, WorkItem> readAll(Long userId, Collection<WorkItemId> ids);

    /** 연결한 순서대로 반환한다. 타인·삭제된 문서와 소유하지 않은 리소스의 연결은 제외한다. */
    List<LinkedSource> readLinkedSources(Long userId, WorkItemId id);
}
