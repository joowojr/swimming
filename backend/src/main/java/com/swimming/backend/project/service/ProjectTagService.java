package com.swimming.backend.project.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.project.domain.ProjectTag;
import com.swimming.backend.project.repository.ProjectTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(
        propagation = Propagation.REQUIRED,
        readOnly = true
)
public class ProjectTagService {

    private final ProjectTagRepository projectTagRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public ProjectTag create(Long userId, String name) {
        String normalizedName = name.trim();
        if (projectTagRepository.existsByUserIdAndName(userId, normalizedName)) {
            throw new BusinessException(ErrorCode.PROJECT_TAG_ALREADY_EXISTS);
        }

        ProjectTag tag = ProjectTag.builder()
                .userId(userId)
                .name(normalizedName)
                .build();
        try {
            return projectTagRepository.saveAndFlush(tag);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.PROJECT_TAG_ALREADY_EXISTS);
        }
    }

    public List<ProjectTag> getAll(Long userId) {
        return projectTagRepository.findAllByUserIdOrderByNameAsc(userId);
    }
}
