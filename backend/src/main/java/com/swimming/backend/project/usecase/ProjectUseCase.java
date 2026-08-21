package com.swimming.backend.project.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.project.domain.Project;
import com.swimming.backend.project.domain.ProjectTag;
import com.swimming.backend.project.dto.CreateProjectRequest;
import com.swimming.backend.project.dto.ProjectDetailResponse;
import com.swimming.backend.project.dto.ProjectProgressResponse;
import com.swimming.backend.project.dto.ProjectResponse;
import com.swimming.backend.project.dto.UpdateProjectRequest;
import com.swimming.backend.project.service.ProjectService;
import com.swimming.backend.project.service.ProjectTagService;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.web.TaskSummaryResponse;
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

        ProjectTag tag = null;
        if (request.newTagName() != null) {
            tag = projectTagService.create(ProjectTag.create(userId, request.newTagName()));
        } else if (request.tagId() != null) {
            tag = projectTagService.getOne(userId, request.tagId());
        }

        Project project = Project.create(
                userId,
                tag,
                request.name(),
                request.description(),
                request.targetDate()
        );
        return ProjectResponse.from(projectService.create(project));
    }

    public List<ProjectResponse> getAll(Long userId) {
        return projectService.getAll(userId)
                .stream()
                .map(ProjectResponse::from)
                .toList();
    }

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
        Project project = projectService.getOne(userId, projectId);
        ProjectTag tag = request.tagId() == null
                ? null
                : projectTagService.getOne(userId, request.tagId());
        project.update(
                request.name(),
                request.description(),
                request.targetDate(),
                request.status(),
                tag
        );
        return ProjectResponse.from(projectService.update(project));
    }
}
