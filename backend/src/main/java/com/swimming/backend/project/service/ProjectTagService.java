package com.swimming.backend.project.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.project.domain.ProjectTag;
import com.swimming.backend.project.repository.ProjectRepository;
import com.swimming.backend.project.repository.ProjectTagRepository;
import com.swimming.backend.project.repository.entity.ProjectTagEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectTagService {

    private final ProjectTagRepository projectTagRepository;
    private final ProjectRepository projectRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public ProjectTag create(ProjectTag projectTag) {
        if (projectTagRepository.existsByUserIdAndName(projectTag.getUserId(), projectTag.getName())) {
            throw new BusinessException(ErrorCode.PROJECT_TAG_ALREADY_EXISTS);
        }

        try {
            return projectTagRepository
                    .saveAndFlush(ProjectTagEntity.from(projectTag))
                    .toDomain();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.PROJECT_TAG_ALREADY_EXISTS);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public ProjectTag getOne(Long userId, Long tagId) {
        return projectTagRepository.findByIdAndUserId(tagId, userId)
                .map(ProjectTagEntity::toDomain)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_TAG_NOT_FOUND));
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<ProjectTag> getAll(Long userId) {
        return projectTagRepository.findAllByUserIdOrderByNameAsc(userId)
                .stream()
                .map(ProjectTagEntity::toDomain)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public ProjectTag update(ProjectTag projectTag) {
        if (projectTagRepository.existsByUserIdAndNameAndIdNot(
                projectTag.getUserId(),
                projectTag.getName(),
                projectTag.getId()
        )) {
            throw new BusinessException(ErrorCode.PROJECT_TAG_ALREADY_EXISTS);
        }

        try {
            if (projectTagRepository.updateOwnedTag(
                    projectTag.getId(),
                    projectTag.getUserId(),
                    projectTag.getName()
            ) != 1) {
                throw new BusinessException(ErrorCode.PROJECT_TAG_NOT_FOUND);
            }
            return projectTag;
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.PROJECT_TAG_ALREADY_EXISTS, exception);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void delete(Long userId, Long tagId) {
        projectRepository.clearTagFromOwnedProjects(userId, tagId);
        if (projectTagRepository.deleteOwnedTag(tagId, userId) != 1) {
            throw new BusinessException(ErrorCode.PROJECT_TAG_NOT_FOUND);
        }
    }
}
