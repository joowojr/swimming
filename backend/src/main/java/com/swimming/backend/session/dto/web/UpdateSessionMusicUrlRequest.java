package com.swimming.backend.session.dto.web;

import jakarta.validation.constraints.Size;

public record UpdateSessionMusicUrlRequest(
        @Size(max = 2048, message = "음악 URL은 2,048자 이하여야 합니다")
        String musicUrl
) {
}
