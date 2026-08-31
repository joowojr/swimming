package com.swimming.backend.project.usecase;

import com.swimming.backend.project.domain.ProjectTag;
import com.swimming.backend.project.dto.ProjectTagNameRequest;
import com.swimming.backend.project.dto.ProjectTagResponse;
import com.swimming.backend.project.service.ProjectTagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectTagUseCase {

    private final ProjectTagService projectTagService;

    public List<ProjectTagResponse> getAll(Long userId) {
        return projectTagService.getAll(userId)
                .stream()
                .map(ProjectTagResponse::from)
                .toList();
    }

    public ProjectTagResponse create(Long userId, ProjectTagNameRequest request) {
        return ProjectTagResponse.from(
                projectTagService.create(ProjectTag.create(userId, request.name()))
        );
    }

    public ProjectTagResponse updateName(
            Long userId,
            Long tagId,
            ProjectTagNameRequest request
    ) {
        return ProjectTagResponse.from(projectTagService.updateName(
                userId,
                tagId,
                request.name()
        ));
    }

    public void delete(Long userId, Long tagId) {
        projectTagService.delete(userId, tagId);
    }
}
