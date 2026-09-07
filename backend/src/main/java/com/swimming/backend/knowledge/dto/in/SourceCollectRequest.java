package com.swimming.backend.knowledge.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Folder 하나에 링크 여러 개를 저장한다.
 */
public record SourceCollectRequest(

        @NotEmpty(message = "저장할 링크를 하나 이상 입력해 주세요")
        @Size(max = 20, message = "한 번에 저장할 수 있는 링크는 20개까지입니다")
        List<@NotBlank @Size(max = 2048) String> urls
) {
}
