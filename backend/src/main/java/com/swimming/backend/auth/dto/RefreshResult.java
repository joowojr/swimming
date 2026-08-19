package com.swimming.backend.auth.dto;

public record RefreshResult(
        RefreshResponse response,
        String refreshCookie
) {
}
