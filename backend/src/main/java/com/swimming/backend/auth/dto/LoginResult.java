package com.swimming.backend.auth.dto;

public record LoginResult(
        AuthResponse response,
        String refreshCookie
) {
}
