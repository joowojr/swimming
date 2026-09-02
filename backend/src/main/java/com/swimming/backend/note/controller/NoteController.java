package com.swimming.backend.note.controller;

import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.note.domain.NoteContextType;
import com.swimming.backend.note.domain.NoteStatus;
import com.swimming.backend.note.dto.in.NoteCreateRequest;
import com.swimming.backend.note.dto.in.NoteCreateResponse;
import com.swimming.backend.note.dto.in.NoteResponse;
import com.swimming.backend.note.dto.in.NoteUpdateRequest;
import com.swimming.backend.note.usecase.NoteUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notes")
public class NoteController {

    private final NoteUseCase noteUseCase;

    @PostMapping
    public ResponseEntity<NoteCreateResponse> create(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody @Valid NoteCreateRequest request
    ) {
        NoteCreateResponse response = noteUseCase.create(
                authUser.id(),
                request
        );

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public ResponseEntity<List<NoteResponse>> getAll(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(defaultValue = "ACTIVE")
            NoteStatus status,
            @RequestParam(required = false)
            NoteContextType contextType,
            @RequestParam(required = false)
            Long folderId,
            @RequestParam(required = false)
            Long sessionId
    ) {
        return ResponseEntity.ok(
                noteUseCase.getAll(
                        authUser.id(),
                        status,
                        contextType,
                        folderId,
                        sessionId
                )
        );
    }

    @PatchMapping("/{noteId}/archive")
    public ResponseEntity<Void> archive(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long noteId
    ) {
        noteUseCase.archive(
                authUser.id(),
                noteId
        );

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{noteId}/restore")
    public ResponseEntity<Void> restore(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long noteId
    ) {
        noteUseCase.restore(
                authUser.id(),
                noteId
        );

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{noteId}")
    public ResponseEntity<NoteResponse> getOne(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long noteId
    ) {
        return ResponseEntity.ok(
                noteUseCase.getOne(
                        authUser.id(),
                        noteId
                )
        );
    }

    @PatchMapping("/{noteId}")
    public ResponseEntity<NoteResponse> update(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long noteId,
            @RequestBody @Valid NoteUpdateRequest request
    ) {
        return ResponseEntity.ok(
                noteUseCase.update(
                        authUser.id(),
                        noteId,
                        request
                )
        );
    }

    @DeleteMapping("/{noteId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long noteId
    ) {
        noteUseCase.delete(
                authUser.id(),
                noteId
        );

        return ResponseEntity
                .noContent()
                .build();
    }
}
