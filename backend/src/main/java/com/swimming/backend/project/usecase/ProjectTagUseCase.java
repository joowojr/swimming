package com.swimming.backend.project.usecase;

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
}
