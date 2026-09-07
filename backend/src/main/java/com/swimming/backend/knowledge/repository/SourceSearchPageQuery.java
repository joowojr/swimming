package com.swimming.backend.knowledge.repository;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** 관계 조건으로 고른 Source 후보를 사용자·폴더·커서로 좁혀 최근 순으로 읽는다. */
public record SourceSearchPageQuery(
        Long userId,
        Long folderId,
        Set<UUID> sourceIds,
        int limit,
        Instant cursorCreatedAt,
        UUID cursorNodeId
) {
}
