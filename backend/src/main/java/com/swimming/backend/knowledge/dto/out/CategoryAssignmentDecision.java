package com.swimming.backend.knowledge.dto.out;

import java.util.UUID;

/** 모델별 확률·거리·인덱스 해석을 마친 저장 독립 판정. */
public sealed interface CategoryAssignmentDecision {
    record Reuse(UUID categoryId) implements CategoryAssignmentDecision {
        public Reuse { java.util.Objects.requireNonNull(categoryId); }
    }
    record Create(String title) implements CategoryAssignmentDecision {
        public Create {
            if (title == null || title.isBlank()) throw new IllegalArgumentException("새 카테고리 이름이 비어 있습니다.");
            title = title.strip();
        }
    }
    /** 제안 이름 없이 판정했는데 맞는 기존 Category가 없다. 새로 만들 이름이 없어 배정하지 않는다. */
    record Skip() implements CategoryAssignmentDecision {}
}
