package com.swimming.backend.project.controller;

import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.project.dto.ProjectTagNameRequest;
import com.swimming.backend.project.dto.ProjectTagResponse;
import com.swimming.backend.project.usecase.ProjectTagUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    @PostMapping
    public ResponseEntity<ProjectTagResponse> create(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody ProjectTagNameRequest request
    ) {
        ProjectTagResponse response = projectTagUseCase.create(authUser.id(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{tagId}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PatchMapping("/{tagId}")
    public ResponseEntity<ProjectTagResponse> update(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long tagId,
            @Valid @RequestBody ProjectTagNameRequest request
    ) {
        return ResponseEntity.ok(projectTagUseCase.update(authUser.id(), tagId, request));
    }

    @DeleteMapping("/{tagId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long tagId
    ) {
        projectTagUseCase.delete(authUser.id(), tagId);
        return ResponseEntity.noContent().build();
    }
}
