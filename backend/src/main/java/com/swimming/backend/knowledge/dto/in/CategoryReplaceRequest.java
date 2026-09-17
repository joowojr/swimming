package com.swimming.backend.knowledge.dto.in;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * 폴더의 Category 구성을 통째로 바꾼다.
 *
 * <p>기존 노드를 유지하지 않는다. 요청에 id가 없고, 서버는 살아 있던 Category를 soft
 * delete한 뒤 요청 항목을 전부 새로 만든다. Preview를 고친 결과든 맨손으로 세운
 * 구성이든 같은 본문이다.
 *
 * <p>빈 목록은 폴더의 Category를 모두 걷어 내는 뜻이다. 개수 조건은 없다.
 */
public record CategoryReplaceRequest(

        @NotNull
        List<@Valid Category> categories
) {

    /**
     * @param sourceIds 최소 1개. 빈 묶음은 폴더를 파생할 수 없어 저장하지 않는다
     */
    public record Category(

            @NotBlank
            @Size(max = 500)
            String title,

            @NotNull
            @NotEmpty
            List<UUID> sourceIds
    ) {
    }
}
