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

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

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
                .findAllByUser_IdAndStatusNotAndDeletedFalseOrderByCreatedAtDesc(
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
    public void validateOwnerships(Long userId, List<Long> projectIds) {
        Set<Long> uniqueProjectIds = Set.copyOf(projectIds);
        if (uniqueProjectIds.isEmpty()) {
            return;
        }
        if (projectRepository.countOwnedActiveByIds(userId, uniqueProjectIds)
                != uniqueProjectIds.size()) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Project update(
            Long userId,
            Long projectId,
            Long tagId,
            String name,
            String description,
            LocalDate targetDate,
            ProjectStatus status
    ) {
        ProjectEntity entity = getOwnedProjectEntity(userId, projectId);
        ProjectTagEntity tagEntity = tagId == null
                ? null
                : getOwnedTagEntity(userId, tagId);
        Project project = entity.toDomain();
        project.update(
                name,
                description,
                targetDate,
                status,
                tagEntity == null ? null : tagEntity.toDomain()
        );
        entity.apply(project, tagEntity);
        projectRepository.flush();
        return entity.toDomain();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void delete(Long userId, Long projectId) {
        ProjectEntity entity = getOwnedProjectEntity(userId, projectId);
        entity.delete();
        projectRepository.flush();
    }

    private ProjectEntity getOwnedProjectEntity(Long userId, Long projectId) {
        return projectRepository.findByIdAndUser_IdAndDeletedFalse(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private ProjectTagEntity getOwnedTagEntity(Long userId, ProjectTag tag) {
        if (tag == null) {
            return null;
        }
        return projectTagRepository.findByIdAndUserId(tag.getId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_TAG_NOT_FOUND));
    }

    private ProjectTagEntity getOwnedTagEntity(Long userId, Long tagId) {
        return projectTagRepository.findByIdAndUserId(tagId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_TAG_NOT_FOUND));
    }
}
