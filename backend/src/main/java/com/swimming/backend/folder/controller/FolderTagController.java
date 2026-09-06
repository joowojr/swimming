package com.swimming.backend.folder.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.folder.dto.FolderTagNameRequest;
import com.swimming.backend.folder.dto.FolderTagResponse;
import com.swimming.backend.folder.usecase.FolderTagUseCase;
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

@Tag(name = "폴더 태그", description = "폴더 생성·수정 모달과 핀보드(`/pinboard`)의 태그 관리 모달.")
@RestController
@RequestMapping("/api/folder-tags")
@RequiredArgsConstructor
public class FolderTagController {

    private final FolderTagUseCase folderTagUseCase;

    @GetMapping
    public ResponseEntity<List<FolderTagResponse>> getAll(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ResponseEntity.ok(folderTagUseCase.getAll(authUser.id()));
    }

    @PostMapping
    public ResponseEntity<FolderTagResponse> create(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody FolderTagNameRequest request
    ) {
        FolderTagResponse response = folderTagUseCase.create(authUser.id(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{folderTagId}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PatchMapping("/{folderTagId}")
    public ResponseEntity<FolderTagResponse> update(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable("folderTagId") Long tagId,
            @Valid @RequestBody FolderTagNameRequest request
    ) {
        return ResponseEntity.ok(folderTagUseCase.updateName(authUser.id(), tagId, request));
    }

    @DeleteMapping("/{folderTagId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable("folderTagId") Long tagId
    ) {
        folderTagUseCase.delete(authUser.id(), tagId);
        return ResponseEntity.noContent().build();
    }
}
