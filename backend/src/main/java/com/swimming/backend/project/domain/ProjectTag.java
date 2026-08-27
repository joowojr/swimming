package com.swimming.backend.project.domain;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ProjectTag {

    private final Long id;
    private final Long userId;
    private String name;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private ProjectTag(
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

    public static ProjectTag create(Long userId, String name) {
        return new ProjectTag(null, userId, name.trim(), null, null);
    }

    public static ProjectTag restore(
            Long id,
            Long userId,
            String name,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        return new ProjectTag(id, userId, name, createdAt, updatedAt);
    }

    public void rename(String name) {
        this.name = name.trim();
    }
}
