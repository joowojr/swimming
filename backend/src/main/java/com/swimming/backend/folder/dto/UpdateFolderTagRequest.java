package com.swimming.backend.folder.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 이 폴더가 가리키는 태그만 바꾼다. 태그 이름 자체는 바꾸지 않아 같은 태그를 쓰는 다른 폴더에 영향이 없다.
 *
 * <p>{@code tagId}는 기존 태그를 달고, {@code newTagName}은 새 태그를 만들어 단다. 둘 다 없으면 태그를 뗀다.
 */
public record UpdateFolderTagRequest(
        Long tagId,

        @Size(max = 30, message = "태그 이름은 30자 이하여야 합니다")
        @Pattern(regexp = ".*\\S.*", message = "태그 이름을 입력해 주세요")
        String newTagName
) {
}
