package com.swimming.backend.note.controller;

import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.note.dto.in.TaskOrganizeRequest;
import com.swimming.backend.note.dto.in.TaskOrganizeResponse;
import com.swimming.backend.note.usecase.TaskOrganizerUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/task-organizer")
public class TaskOrganizerController {

    private final TaskOrganizerUseCase taskOrganizerUseCase;

    @PostMapping("/preview")
    public ResponseEntity<TaskOrganizeResponse> preview(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody @Valid TaskOrganizeRequest request
    ) {
        TaskOrganizeResponse response =
                taskOrganizerUseCase.preview(
                        authUser.id(),
                        request
                );

        return ResponseEntity.ok(response);
    }
}