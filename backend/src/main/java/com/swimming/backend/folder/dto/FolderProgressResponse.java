package com.swimming.backend.folder.dto;

public record FolderProgressResponse(
        int totalTaskCount,
        int completedTaskCount,
        int completionPct
) {
}
