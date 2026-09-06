package com.swimming.backend.folder.dto;

import com.swimming.backend.folder.domain.Folder;
import com.swimming.backend.folder.domain.FolderStatus;
import com.swimming.backend.common.dto.CursorPage;
import com.swimming.backend.task.dto.in.TaskSummaryResponse;

import java.time.LocalDate;

/**
 * 폴더 하나와 그 안의 할 일 첫 페이지.
 *
 * <p>{@code progress}는 페이지가 아니라 폴더 전체를 센다. 목록을 끊어 읽어도 "12/20 완료"는
 * 그대로여야 한다.
 */
public record FolderDetailResponse(
        Long id,
        String name,
        String description,
        LocalDate targetDate,
        FolderStatus status,
        FolderTagResponse tag,
        FolderProgressResponse progress,
        CursorPage<TaskSummaryResponse> tasks
) {
    public static FolderDetailResponse from(
            Folder folder,
            FolderProgressResponse progress,
            CursorPage<TaskSummaryResponse> tasks
    ) {
        return new FolderDetailResponse(
                folder.getId(),
                folder.getName(),
                folder.getDescription(),
                folder.getTargetDate(),
                folder.getStatus(),
                folder.getTag() == null
                        ? null
                        : FolderTagResponse.from(folder.getTag()),
                progress,
                tasks
        );
    }
}
