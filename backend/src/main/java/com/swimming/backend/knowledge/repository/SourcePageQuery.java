package com.swimming.backend.knowledge.repository;

import com.swimming.backend.knowledge.domain.SourceProcessingStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Folder 안의 Source를 최근 순으로 한 페이지 읽는다.
 *
 * @param status         null이면 상태를 가리지 않는다
 * @param cursorCreatedAt null이면 첫 페이지
 * @param cursorNodeId   {@code cursorCreatedAt}과 항상 함께 온다
 */
public record SourcePageQuery(
        Long userId,
        Long folderId,
        SourceProcessingStatus status,
        int limit,
        Instant cursorCreatedAt,
        UUID cursorNodeId
) {
}
