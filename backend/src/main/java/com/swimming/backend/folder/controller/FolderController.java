package com.swimming.backend.folder.controller;

import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.folder.dto.CreateFolderRequest;
import com.swimming.backend.folder.dto.FolderDetailResponse;
import com.swimming.backend.folder.dto.FolderResponse;
import com.swimming.backend.folder.dto.UpdateFolderRequest;
import com.swimming.backend.folder.usecase.FolderUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderUseCase folderUseCase;

    @PostMapping
    public ResponseEntity<FolderResponse> create(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody CreateFolderRequest request
    ) {
        FolderResponse response = folderUseCase.create(authUser.id(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public ResponseEntity<List<FolderResponse>> getAll(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ResponseEntity.ok(folderUseCase.getAll(authUser.id()));
    }

    @GetMapping("/{folderId}")
    public ResponseEntity<FolderDetailResponse> getOne(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long folderId
    ) {
        return ResponseEntity.ok(
                folderUseCase.getOne(authUser.id(), folderId)
        );
    }

    @PatchMapping("/{folderId}")
    public ResponseEntity<FolderResponse> update(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long folderId,
            @Valid @RequestBody UpdateFolderRequest request
    ) {
        return ResponseEntity.ok(
                folderUseCase.update(authUser.id(), folderId, request)
        );
    }

    @DeleteMapping("/{folderId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long folderId
    ) {
        folderUseCase.delete(authUser.id(), folderId);
        return ResponseEntity.noContent().build();
    }
}
