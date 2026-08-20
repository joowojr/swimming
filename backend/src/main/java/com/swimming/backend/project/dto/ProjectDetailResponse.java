package com.swimming.backend.project.dto;

import com.swimming.backend.project.domain.Project;
import com.swimming.backend.project.domain.ProjectStatus;
import com.swimming.backend.task.dto.web.TaskSummaryResponse;

import java.time.LocalDate;
import java.util.List;

public record ProjectDetailResponse(
        Long id,
        String name,
        String description,
        LocalDate targetDate,
        ProjectStatus status,
        ProjectTagResponse tag,
        ProjectProgressResponse progress,
        List<TaskSummaryResponse> tasks
) {
    public static ProjectDetailResponse from(
            Project project,
            ProjectProgressResponse progress,
            List<TaskSummaryResponse> tasks
    ) {
        return new ProjectDetailResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getTargetDate(),
                project.getStatus(),
                project.getTag() == null
                        ? null
                        : ProjectTagResponse.from(project.getTag()),
                progress,
                tasks
        );
    }
}
