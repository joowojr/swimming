package com.swimming.backend.knowledge.dto.in;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** sourceId가 없으면 전체 문서를, 있으면 해당 문서만 기존 대상 카테고리로 옮긴다. */
public record CategoryMergeRequest(@NotNull UUID targetCategoryId, UUID sourceId) {
}
