package com.swimming.backend.folder.dto;

import com.swimming.backend.folder.domain.FolderTag;

public record FolderTagResponse(
        Long id,
        String name
) {
    public static FolderTagResponse from(FolderTag tag) {
        return new FolderTagResponse(tag.getId(), tag.getName());
    }
}
