package com.swimming.backend.auth.service;

public record GoogleIdentity(String subject, String email, String nickname) {
}
