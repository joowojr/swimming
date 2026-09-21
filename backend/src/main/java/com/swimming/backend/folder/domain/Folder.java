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
     * 이 폴더에 살아 있는 링크의 개수. 링크 저장·삭제 시 전용 메서드로 변경한다.
     * 일반 폴더 정보 수정인 {@link #update}는 건드리지 않는다.
     */
    private long sourceCount;

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
            long sourceCount,
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
        this.sourceCount = sourceCount;
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
                FolderStatus.NOT_STARTED,
                false,
                0,
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
            long sourceCount,
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
                sourceCount,
                pinnedAt,
                createdAt,
                updatedAt
        );
    }

    public boolean isHasSource() {
        return sourceCount > 0;
    }

    public void incrementSourceCount() {
        this.sourceCount++;
    }

    public void decrementSourceCount() {
        if (sourceCount <= 0) {
            throw new IllegalStateException("folder source count cannot be negative");
        }
        this.sourceCount--;
    }

    public void update(
            String name,
            String description,
            LocalDate targetDate,
            FolderTag tag
    ) {
        this.name = name.trim();
        this.description = description.trim();
        this.targetDate = targetDate;
        this.tag = tag;
    }

    public void updateStatus(FolderStatus status) {
        this.status = status;
    }

    public void delete() {
        this.deleted = true;
    }
}
