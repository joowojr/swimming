package com.swimming.backend.user.dto;

public record UserAuthInfo(
        Long id,
        String email,
        String nickname,
        String timezone
) {
}
