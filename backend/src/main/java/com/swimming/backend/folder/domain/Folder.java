package com.swimming.backend.folder.domain;

import lombok.Getter;

import java.time.LocalDate;
import java.time.Instant;

@Getter
public class Folder {

    private final Long id;
    private final Long userId;
    private FolderTag tag;
    private String name;
    private String description;
    private LocalDate targetDate;
    private FolderStatus status;
    private boolean deleted;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Folder(
            Long id,
            Long userId,
            FolderTag tag,
            String name,
            String description,
            LocalDate targetDate,
            FolderStatus status,
            boolean deleted,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.tag = tag;
        this.name = name;
        this.description = description;
        this.targetDate = targetDate;
        this.status = status;
        this.deleted = deleted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Folder create(
            Long userId,
            FolderTag tag,
            String name,
            String description,
            LocalDate targetDate
    ) {
        return new Folder(
                null,
                userId,
                tag,
                name.trim(),
                description.trim(),
                targetDate,
                FolderStatus.IN_PROGRESS,
                false,
                null,
                null
        );
    }

    public static Folder restore(
            Long id,
            Long userId,
            FolderTag tag,
            String name,
            String description,
            LocalDate targetDate,
            FolderStatus status,
            boolean deleted,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new Folder(
                id,
                userId,
                tag,
                name,
                description,
                targetDate,
                status,
                deleted,
                createdAt,
                updatedAt
        );
    }

    public void update(
            String name,
            String description,
            LocalDate targetDate,
            FolderStatus status,
            FolderTag tag
    ) {
        this.name = name.trim();
        this.description = description.trim();
        this.targetDate = targetDate;
        this.status = status;
        this.tag = tag;
    }

    public void delete() {
        this.deleted = true;
    }
}
