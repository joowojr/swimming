package com.swimming.backend.project.dto;

import com.swimming.backend.project.domain.Project;

public record ProjectReference(
        Long id,
        String name,
        String description
) {

    public static ProjectReference from(Project project) {
        return new ProjectReference(
                project.getId(),
                project.getName(),
                project.getDescription()
        );
    }
}
