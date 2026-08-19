package com.swimming.backend.user.dto;

public record UserAuthInfo(
        Long id,
        String email,
        String passwordHash,
        String nickname,
        String timezone
) {
}
