package com.swimming.backend.folder.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * 폴더 목록을 어떤 상태로 좁혀 볼지 고르는 값이다. 목록 조회의 {@code status} 파라미터가 쓴다.
 * 기본값인 {@link #ACTIVE}는 보관한 폴더를 뺀 나머지이고, {@link #ALL}은 보관까지 모두 본다.
 */
public enum FolderStatusFilter {

    ACTIVE(EnumSet.of(FolderStatus.NOT_STARTED, FolderStatus.IN_PROGRESS)),
    ALL(EnumSet.allOf(FolderStatus.class)),
    NOT_STARTED(EnumSet.of(FolderStatus.NOT_STARTED)),
    IN_PROGRESS(EnumSet.of(FolderStatus.IN_PROGRESS)),
    ARCHIVED(EnumSet.of(FolderStatus.ARCHIVED));

    private final Set<FolderStatus> statuses;

    FolderStatusFilter(Set<FolderStatus> statuses) {
        this.statuses = statuses;
    }

    public Set<FolderStatus> getStatuses() {
        return statuses;
    }
}
