package com.swimming.backend.folder.dto;

import com.swimming.backend.folder.domain.Folder;
import com.swimming.backend.folder.domain.FolderStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 폴더 하나.
 *
 * <p>할 일 목록은 담지 않는다. 목록은 페이지 단위로 이어 읽어야 해서 폴더 정보와 갱신 시점이
 * 다르다. 한 응답에 묶으면 다음 페이지를 받을 때마다 폴더 정보까지 다시 실려 온다.
 * {@code GET /api/folders/{folderId}/tasks}가 따로 맡는다.
 */
public record FolderDetailResponse(
        Long id,
        String name,
        String description,
        LocalDate targetDate,
        FolderStatus status,
        FolderTagResponse tag,
        Instant pinnedAt,
        long sourceCount
) {
    public static FolderDetailResponse from(Folder folder) {
        return new FolderDetailResponse(
                folder.getId(),
                folder.getName(),
                folder.getDescription(),
                folder.getTargetDate(),
                folder.getStatus(),
                folder.getTag() == null
                        ? null
                        : FolderTagResponse.from(folder.getTag()),
                folder.getPinnedAt(),
                folder.getSourceCount()
        );
    }
}
