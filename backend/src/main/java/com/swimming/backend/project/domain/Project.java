package com.swimming.backend.project.domain;

import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
public class Project {

    private final Long id;
    private final Long userId;
    private ProjectTag tag;
    private String name;
    private String description;
    private LocalDate targetDate;
    private ProjectStatus status;
    private boolean deleted;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private Project(
            Long id,
            Long userId,
            ProjectTag tag,
            String name,
            String description,
            LocalDate targetDate,
            ProjectStatus status,
            boolean deleted,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
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

    public static Project create(
            Long userId,
            ProjectTag tag,
            String name,
            String description,
            LocalDate targetDate
    ) {
        return new Project(
                null,
                userId,
                tag,
                name.trim(),
                description.trim(),
                targetDate,
                ProjectStatus.IN_PROGRESS,
                false,
                null,
                null
        );
    }

    public static Project restore(
            Long id,
            Long userId,
            ProjectTag tag,
            String name,
            String description,
            LocalDate targetDate,
            ProjectStatus status,
            boolean deleted,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        return new Project(
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
            ProjectStatus status,
            ProjectTag tag
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
