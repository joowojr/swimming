package com.swimming.backend.knowledge.dto.in;

import java.util.List;
import java.util.UUID;

/**
 * 방금 저장한 Category 구성.
 *
 * <p>매번 새 노드를 만들므로 {@code nodeId}가 바뀐다. 화면은 이 응답으로 배지·그래프를
 * 다시 그린다.
 */
public record CategoryReplaceResponse(List<Category> categories) {

    public record Category(UUID nodeId, String title, List<UUID> sourceIds) {
    }
}
