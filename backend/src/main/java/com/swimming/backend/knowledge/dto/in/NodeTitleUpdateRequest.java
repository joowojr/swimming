package com.swimming.backend.knowledge.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Category 또는 Topic 노드의 이름만 변경한다.
 *
 * @param title 변경할 제목. 1~500자이며 양끝 공백은 저장 전에 제거한다
 */
public record NodeTitleUpdateRequest(
        @NotBlank
        @Size(max = 500)
        String title
) {
}
