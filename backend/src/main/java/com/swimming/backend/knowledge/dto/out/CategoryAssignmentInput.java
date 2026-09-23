package com.swimming.backend.knowledge.dto.out;

import java.util.List;

/**
 * 판정기에만 전달하는 값. 제목은 A, 제안 이름은 B/C가 사용한다.
 * 제안 이름은 비어 있을 수 있다. 그때는 요약만으로 기존 Category 재사용만 판정한다.
 * 기존 Category 후보에는 그 묶음에 포함된 Source의 대표 Topic을 함께 담는다.
 */
public record CategoryAssignmentInput(String title, String summary, String proposedCategoryTitle,
                                      List<CategoryAssignmentCandidate> categories) {
    public CategoryAssignmentInput {
        categories = List.copyOf(categories);
    }
}
