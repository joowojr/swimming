package com.swimming.backend.auth.dto;

public record AuthResponse(String accessToken, User user) {

    public record User(Long id, String email, String nickname, String timezone) {
    }
}
