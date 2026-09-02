package com.swimming.backend.folder.dto;

import com.swimming.backend.folder.domain.Folder;

public record FolderReference(
        Long id,
        String name,
        String description
) {

    public static FolderReference from(Folder folder) {
        return new FolderReference(
                folder.getId(),
                folder.getName(),
                folder.getDescription()
        );
    }
}
