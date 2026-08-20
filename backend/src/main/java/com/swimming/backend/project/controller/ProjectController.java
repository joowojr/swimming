package com.swimming.backend.project.controller;

import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.project.dto.CreateProjectRequest;
import com.swimming.backend.project.dto.ProjectDetailResponse;
import com.swimming.backend.project.dto.ProjectResponse;
import com.swimming.backend.project.dto.UpdateProjectRequest;
import com.swimming.backend.project.usecase.ProjectUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectUseCase projectUseCase;

    @PostMapping
    public ResponseEntity<ProjectResponse> create(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody CreateProjectRequest request
    ) {
        ProjectResponse response = projectUseCase.create(authUser.id(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> getAll(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ResponseEntity.ok(projectUseCase.getAll(authUser.id()));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectDetailResponse> getOne(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long projectId
    ) {
        return ResponseEntity.ok(
                projectUseCase.getOne(authUser.id(), projectId)
        );
    }

    @PatchMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> update(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long projectId,
            @Valid @RequestBody UpdateProjectRequest request
    ) {
        return ResponseEntity.ok(
                projectUseCase.update(authUser.id(), projectId, request)
        );
    }
}
