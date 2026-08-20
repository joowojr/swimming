package com.swimming.backend.project.dto;

import com.swimming.backend.project.domain.Project;
import com.swimming.backend.project.domain.ProjectStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProjectResponse(
        Long id,
        String name,
        String description,
        LocalDate targetDate,
        ProjectStatus status,
        ProjectTagResponse tag,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getTargetDate(),
                project.getStatus(),
                project.getTag() == null
                        ? null
                        : ProjectTagResponse.from(project.getTag()),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
