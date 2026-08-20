package com.swimming.backend.project.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.project.domain.ProjectTag;
import com.swimming.backend.project.dto.CreateProjectRequest;
import com.swimming.backend.project.dto.ProjectResponse;
import com.swimming.backend.project.dto.UpdateProjectRequest;
import com.swimming.backend.project.service.ProjectService;
import com.swimming.backend.project.service.ProjectTagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectUseCase {

    private final ProjectService projectService;
    private final ProjectTagService projectTagService;

    @Transactional(propagation = Propagation.REQUIRED)
    public ProjectResponse create(Long userId, CreateProjectRequest request) {
        if (request.tagId() != null && request.newTagName() != null) {
            throw new BusinessException(ErrorCode.PROJECT_TAG_SELECTION_CONFLICT);
        }

        Long tagId = request.tagId();
        if (request.newTagName() != null) {
            ProjectTag createdTag = projectTagService.create(userId, request.newTagName());
            tagId = createdTag.getId();
        }

        return ProjectResponse.from(projectService.create(
                userId,
                request.name(),
                request.description(),
                request.targetDate(),
                tagId
        ));
    }

    public List<ProjectResponse> getAll(Long userId) {
        return projectService.getAll(userId)
                .stream()
                .map(ProjectResponse::from)
                .toList();
    }

    public ProjectResponse getOne(Long userId, Long projectId) {
        return ProjectResponse.from(projectService.getOne(userId, projectId));
    }

    public ProjectResponse update(
            Long userId,
            Long projectId,
            UpdateProjectRequest request
    ) {
        return ProjectResponse.from(projectService.update(
                userId,
                projectId,
                request.name(),
                request.description(),
                request.targetDate(),
                request.status(),
                request.tagId()
        ));
    }
}
