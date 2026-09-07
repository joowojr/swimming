package com.swimming.backend.knowledge.dto.in;

/** 삭제한 Source가 속했던 폴더와 삭제 후 활성 Source 존재 여부. */
public record SourceDeleteResponse(
        Long folderId,
        boolean hasSource
) {
}
