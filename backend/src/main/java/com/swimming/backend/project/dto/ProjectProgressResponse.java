package com.swimming.backend.project.dto;

public record ProjectProgressResponse(
        int totalTaskCount,
        int completedTaskCount,
        int completionPct
) {
}
