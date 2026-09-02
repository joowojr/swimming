package com.swimming.backend.folder.domain;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class FolderTag {

    private final Long id;
    private final Long userId;
    private String name;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private FolderTag(
            Long id,
            Long userId,
            String name,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static FolderTag create(Long userId, String name) {
        return new FolderTag(null, userId, name.trim(), null, null);
    }

    public static FolderTag restore(
            Long id,
            Long userId,
            String name,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        return new FolderTag(id, userId, name, createdAt, updatedAt);
    }

    public void rename(String name) {
        this.name = name.trim();
    }
}
