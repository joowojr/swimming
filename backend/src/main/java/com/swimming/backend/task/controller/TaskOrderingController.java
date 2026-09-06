package com.swimming.backend.task.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.task.dto.in.TaskMatrixPageQuery;
import com.swimming.backend.task.dto.in.TaskPlacementRequest;
import com.swimming.backend.task.dto.out.TaskMatrixPageResponse;
import com.swimming.backend.task.dto.out.TaskPlacementResponse;
import com.swimming.backend.task.usecase.TaskOrderingUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "할 일 매트릭스", description = "할 일 화면(`/tasks`)의 매트릭스 뷰. 중요·긴급 사분면 조회와 이동.")
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskOrderingController {

    private final TaskOrderingUseCase taskOrderingUseCase;

    @GetMapping("/matrix")
    public ResponseEntity<TaskMatrixPageResponse> getMatrixPage(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String section,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String status
    ) {
        TaskMatrixPageQuery query = TaskMatrixPageQuery.from(section, size, cursor, status);
        return ResponseEntity.ok(taskOrderingUseCase.getMatrixPage(authUser.id(), query));
    }

    @PatchMapping("/{taskId}/placement")
    public ResponseEntity<TaskPlacementResponse> move(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long taskId,
            @Valid @RequestBody TaskPlacementRequest request
    ) {
        return ResponseEntity.ok(taskOrderingUseCase.move(authUser.id(), taskId, request));
    }
}
