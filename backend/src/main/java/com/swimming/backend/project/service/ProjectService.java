package com.swimming.backend.project.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.project.domain.Project;
import com.swimming.backend.project.domain.ProjectStatus;
import com.swimming.backend.project.domain.ProjectTag;
import com.swimming.backend.project.dto.ProjectReference;
import com.swimming.backend.project.repository.ProjectRepository;
import com.swimming.backend.project.repository.ProjectTagRepository;
import com.swimming.backend.project.repository.entity.ProjectEntity;
import com.swimming.backend.project.repository.entity.ProjectTagEntity;
import com.swimming.backend.user.domain.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectTagRepository projectTagRepository;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRED)
    public Project create(Project project) {
        ProjectTagEntity tagEntity = getOwnedTagEntity(project.getUserId(), project.getTag());
        User user = entityManager.getReference(User.class, project.getUserId());
        return projectRepository.saveAndFlush(
                ProjectEntity.from(project, user, tagEntity)
        ).toDomain();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<Project> getAll(Long userId) {
        return projectRepository
                .findAllByUser_IdAndStatusNotOrderByCreatedAtDesc(
                        userId,
                        ProjectStatus.ARCHIVED
                )
                .stream()
                .map(ProjectEntity::toDomain)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Project getOne(Long userId, Long projectId) {
        return getOwnedProjectEntity(userId, projectId).toDomain();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public ProjectReference getReference(Long userId, Long projectId) {
        return ProjectReference.from(getOwnedProjectEntity(userId, projectId).toDomain());
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public void validateOwnership(Long userId, Long projectId) {
        getOwnedProjectEntity(userId, projectId);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Project update(Project project) {
        ProjectEntity projectEntity = getOwnedProjectEntity(project.getUserId(), project.getId());
        ProjectTagEntity tagEntity = getOwnedTagEntity(project.getUserId(), project.getTag());
        projectEntity.apply(project, tagEntity);
        return projectRepository.saveAndFlush(projectEntity).toDomain();
    }

    private ProjectEntity getOwnedProjectEntity(Long userId, Long projectId) {
        return projectRepository.findByIdAndUser_Id(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private ProjectTagEntity getOwnedTagEntity(Long userId, ProjectTag tag) {
        if (tag == null) {
            return null;
        }
        return projectTagRepository.findByIdAndUserId(tag.getId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_TAG_NOT_FOUND));
    }
}
