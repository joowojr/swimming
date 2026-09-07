package com.swimming.backend.folder.dto;

import com.swimming.backend.folder.domain.Folder;
import com.swimming.backend.folder.domain.FolderStatus;

import java.time.LocalDate;
import java.time.Instant;

public record FolderResponse(
        Long id,
        String name,
        String description,
        LocalDate targetDate,
        FolderStatus status,
        FolderTagResponse tag,
        boolean hasSource,
        Instant createdAt,
        Instant updatedAt
) {
    public static FolderResponse from(Folder folder) {
        return new FolderResponse(
                folder.getId(),
                folder.getName(),
                folder.getDescription(),
                folder.getTargetDate(),
                folder.getStatus(),
                folder.getTag() == null
                        ? null
                        : FolderTagResponse.from(folder.getTag()),
                folder.isHasSource(),
                folder.getCreatedAt(),
                folder.getUpdatedAt()
        );
    }
}
