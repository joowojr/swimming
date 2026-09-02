package com.swimming.backend.folder.dto;

import com.swimming.backend.folder.domain.Folder;
import com.swimming.backend.folder.domain.FolderStatus;
import com.swimming.backend.task.dto.in.TaskSummaryResponse;

import java.time.LocalDate;
import java.util.List;

public record FolderDetailResponse(
        Long id,
        String name,
        String description,
        LocalDate targetDate,
        FolderStatus status,
        FolderTagResponse tag,
        FolderProgressResponse progress,
        List<TaskSummaryResponse> tasks
) {
    public static FolderDetailResponse from(
            Folder folder,
            FolderProgressResponse progress,
            List<TaskSummaryResponse> tasks
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
