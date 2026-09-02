package com.swimming.backend.session.controller;

import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.session.dto.web.EndSessionRequest;
import com.swimming.backend.session.dto.web.SessionResponse;
import com.swimming.backend.session.dto.web.SessionDetailResponse;
import com.swimming.backend.session.dto.web.StartPersonalSessionRequest;
import com.swimming.backend.session.dto.web.UpdateSessionMusicUrlRequest;
import com.swimming.backend.session.dto.web.UpdateSessionFocusDurationRequest;
import com.swimming.backend.session.usecase.SessionUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionUseCase sessionUseCase;

    @GetMapping
    public ResponseEntity<List<SessionDetailResponse>> getAll(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ResponseEntity.ok(sessionUseCase.getAll(authUser.id()));
    }

    @PostMapping
    public ResponseEntity<SessionResponse> startPersonal(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody StartPersonalSessionRequest request
    ) {
        SessionResponse response = sessionUseCase.startPersonal(authUser.id(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/active")
    public ResponseEntity<SessionDetailResponse> getActive(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return sessionUseCase.getActive(authUser.id())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionDetailResponse> get(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long sessionId
    ) {
        return ResponseEntity.ok(sessionUseCase.get(authUser.id(), sessionId));
    }

    @PostMapping("/{sessionId}/end")
    public ResponseEntity<SessionResponse> end(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long sessionId,
            @Valid @RequestBody(required = false) EndSessionRequest request
    ) {
        return ResponseEntity.ok(sessionUseCase.end(authUser.id(), sessionId, request));
    }

    @PutMapping("/{sessionId}/music-url")
    public ResponseEntity<Void> updateMusicUrl(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long sessionId,
            @Valid @RequestBody UpdateSessionMusicUrlRequest request
    ) {
        sessionUseCase.updateMusicUrl(authUser.id(), sessionId, request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{sessionId}/focus-duration")
    public ResponseEntity<Void> updateFocusDuration(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long sessionId,
            @Valid @RequestBody UpdateSessionFocusDurationRequest request
    ) {
        sessionUseCase.updateFocusDuration(authUser.id(), sessionId, request);
        return ResponseEntity.noContent().build();
    }
}
