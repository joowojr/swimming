package com.swimming.backend.folder.dto;

import com.swimming.backend.folder.domain.Folder;
import com.swimming.backend.folder.domain.FolderStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record FolderResponse(
        Long id,
        String name,
        String description,
        LocalDate targetDate,
        FolderStatus status,
        FolderTagResponse tag,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
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
                folder.getCreatedAt(),
                folder.getUpdatedAt()
        );
    }
}
