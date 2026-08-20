package com.swimming.backend.project.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.project.domain.Project;
import com.swimming.backend.project.domain.ProjectStatus;
import com.swimming.backend.project.domain.ProjectTag;
import com.swimming.backend.project.repository.ProjectRepository;
import com.swimming.backend.project.repository.ProjectTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(
        propagation = Propagation.REQUIRED,
        readOnly = true
)
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectTagRepository projectTagRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public Project create(
            Long userId,
            String name,
            String description,
            LocalDate targetDate,
            Long tagId
    ) {
        ProjectTag tag = getOwnedTag(userId, tagId);
        Project project = Project.builder()
                .userId(userId)
                .tag(tag)
                .name(name.trim())
                .description(description.trim())
                .targetDate(targetDate)
                .build();
        return projectRepository.save(project);
    }

    public List<Project> getAll(Long userId) {
        return projectRepository
                .findAllByUserIdAndStatusNotOrderByCreatedAtDesc(
                        userId,
                        ProjectStatus.ARCHIVED
                );
    }

    public Project getOne(Long userId, Long projectId) {
        return getOwnedProject(userId, projectId);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Project update(
            Long userId,
            Long projectId,
            String name,
            String description,
            LocalDate targetDate,
            ProjectStatus status,
            Long tagId
    ) {
        Project project = getOwnedProject(userId, projectId);
        ProjectTag tag = getOwnedTag(userId, tagId);
        project.update(
                name.trim(),
                description.trim(),
                targetDate,
                status,
                tag
        );
        return project;
    }

    private Project getOwnedProject(Long userId, Long projectId) {
        return projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private ProjectTag getOwnedTag(Long userId, Long tagId) {
        if (tagId == null) {
            return null;
        }
        return projectTagRepository.findByIdAndUserId(tagId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_TAG_NOT_FOUND));
    }
}
