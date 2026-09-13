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

    /**
     * 이 폴더에 살아 있는 링크가 있는지. 링크 도메인이 갱신하는 파생 상태라 여기서는
     * 읽기만 한다. {@link #update}가 건드리지 않는다.
     */
    private final boolean hasSource;

    /**
     * 이 폴더를 고정한 시각. 고정하지 않았으면 null이다. 전용 API가 정하는 값이라
     * {@link #update}가 건드리지 않는다.
     */
    private final Instant pinnedAt;

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
            boolean hasSource,
            Instant pinnedAt,
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
        this.hasSource = hasSource;
        this.pinnedAt = pinnedAt;
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
                false,
                null,
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
            boolean hasSource,
            Instant pinnedAt,
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
                hasSource,
                pinnedAt,
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
