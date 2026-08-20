package com.swimming.backend.project.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.project.domain.ProjectTag;
import com.swimming.backend.project.dto.CreateProjectRequest;
import com.swimming.backend.project.dto.ProjectDetailResponse;
import com.swimming.backend.project.dto.ProjectProgressResponse;
import com.swimming.backend.project.dto.ProjectResponse;
import com.swimming.backend.project.dto.UpdateProjectRequest;
import com.swimming.backend.project.service.ProjectService;
import com.swimming.backend.project.service.ProjectTagService;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.TaskSummaryResponse;
import com.swimming.backend.task.service.TaskService;
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
    private final TaskService taskService;

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

    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public List<ProjectResponse> getAll(Long userId) {
        return projectService.getAll(userId)
                .stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public ProjectDetailResponse getOne(Long userId, Long projectId) {
        var project = projectService.getOne(userId, projectId);
        List<TaskSummaryResponse> tasks = taskService.getSummaries(project.getId());
        int totalTaskCount = tasks.size();
        int completedTaskCount = (int) tasks.stream()
                .filter(task -> task.status() == TaskStatus.DONE)
                .count();
        int completionPct = totalTaskCount == 0
                ? 0
                : completedTaskCount * 100 / totalTaskCount;
        ProjectProgressResponse progress = new ProjectProgressResponse(
                totalTaskCount,
                completedTaskCount,
                completionPct
        );
        return ProjectDetailResponse.from(project, progress, tasks);
    }

    @Transactional(propagation = Propagation.REQUIRED)
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
