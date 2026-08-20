package com.swimming.backend.project.dto;

import com.swimming.backend.project.domain.ProjectTag;

public record ProjectTagResponse(
        Long id,
        String name
) {
    public static ProjectTagResponse from(ProjectTag tag) {
        return new ProjectTagResponse(tag.getId(), tag.getName());
    }
}
