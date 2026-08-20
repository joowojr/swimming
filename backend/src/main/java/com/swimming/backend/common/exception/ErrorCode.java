package com.swimming.backend.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    DATABASE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "데이터베이스 연결을 확인할 수 없습니다"),

    // AUTH
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "인증을 갱신할 수 없습니다"),

    // PRJOECT DOMAIN
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다"),
    PROJECT_TAG_NOT_FOUND(HttpStatus.NOT_FOUND, "프로젝트 태그를 찾을 수 없습니다"),
    PROJECT_TAG_SELECTION_CONFLICT(HttpStatus.BAD_REQUEST, "기존 태그와 새 태그를 동시에 선택할 수 없습니다"),
    PROJECT_TAG_ALREADY_EXISTS(HttpStatus.CONFLICT, "같은 이름의 프로젝트 태그가 이미 있습니다");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
