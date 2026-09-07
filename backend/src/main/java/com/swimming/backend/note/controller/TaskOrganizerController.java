package com.swimming.backend.note.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.note.dto.in.TaskOrganizeRequest;
import com.swimming.backend.note.dto.in.TaskOrganizeResponse;
import com.swimming.backend.note.dto.in.TaskOrganizeConfirmRequest;
import com.swimming.backend.note.dto.in.TaskOrganizeConfirmResponse;
import com.swimming.backend.note.usecase.TaskOrganizerUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "메모에서 할 일 뽑기 (AI)", description = "메모 카드 안의 할 일 정리 패널. 메모 글에서 할 일 후보를 뽑아 확정한다.")
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

    @PostMapping("/confirm")
    public ResponseEntity<TaskOrganizeConfirmResponse> confirm(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody @Valid TaskOrganizeConfirmRequest request
    ) {
        return ResponseEntity.ok(
                taskOrganizerUseCase.confirm(authUser.id(), request)
        );
    }
}
