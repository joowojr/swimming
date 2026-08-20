package com.swimming.backend.project.controller;

import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.project.dto.ProjectTagResponse;
import com.swimming.backend.project.usecase.ProjectTagUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/project-tags")
@RequiredArgsConstructor
public class ProjectTagController {

    private final ProjectTagUseCase projectTagUseCase;

    @GetMapping
    public ResponseEntity<List<ProjectTagResponse>> getAll(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ResponseEntity.ok(projectTagUseCase.getAll(authUser.id()));
    }
}
