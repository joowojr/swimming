package com.swimming.backend.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    DATABASE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "데이터베이스 연결을 확인할 수 없습니다"),

    // AUTH
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "인증을 갱신할 수 없습니다"),
    INVALID_GOOGLE_TOKEN(HttpStatus.UNAUTHORIZED, "Google 인증 토큰이 올바르지 않습니다"),

    // FOLDER DOMAIN
    FOLDER_NOT_FOUND(HttpStatus.NOT_FOUND, "폴더를 찾을 수 없습니다"),
    FOLDER_HAS_TASKS(HttpStatus.CONFLICT, "폴더의 할 일을 모두 삭제한 후 폴더를 삭제할 수 있습니다"),
    FOLDER_HAS_SOURCES(HttpStatus.CONFLICT, "폴더의 링크를 모두 삭제한 후 폴더를 삭제할 수 있습니다"),
    FOLDER_TAG_NOT_FOUND(HttpStatus.NOT_FOUND, "폴더 태그를 찾을 수 없습니다"),
    FOLDER_TAG_SELECTION_CONFLICT(HttpStatus.BAD_REQUEST, "기존 태그와 새 태그를 동시에 선택할 수 없습니다"),
    FOLDER_TAG_ALREADY_EXISTS(HttpStatus.CONFLICT, "같은 이름의 폴더 태그가 이미 있습니다"),

    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "Task를 찾을 수 없습니다"),
    INVALID_TASK_STATUS(HttpStatus.BAD_REQUEST, "Task 상태가 올바르지 않습니다"),
    INVALID_TASK_SORT(HttpStatus.BAD_REQUEST, "Task 정렬 조건이 올바르지 않습니다"),
    INVALID_MATRIX_SECTION(HttpStatus.BAD_REQUEST, "Matrix 영역이 올바르지 않습니다"),
    INVALID_MATRIX_CURSOR(HttpStatus.BAD_REQUEST, "Matrix 조회 커서가 올바르지 않습니다"),
    INVALID_TASK_CURSOR(HttpStatus.BAD_REQUEST, "할 일 목록 조회 커서가 올바르지 않습니다"),
    INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST, "페이지 크기가 올바르지 않습니다"),
    INVALID_TASK_PLACEMENT(HttpStatus.BAD_REQUEST, "Task 이동 위치가 올바르지 않습니다"),
    TASK_PLACEMENT_CONFLICT(HttpStatus.CONFLICT, "Task 목록이 변경되어 이동 위치를 적용할 수 없습니다"),
    TASK_SOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "연결할 링크를 찾을 수 없습니다"),

    // PLAN DOMAIN
    INVALID_DAILY_PLAN_TASKS(HttpStatus.BAD_REQUEST, "오늘의 계획 Task 목록이 올바르지 않습니다"),
//    INVALID_DAILY_PLAN_DATE_RANGE(HttpStatus.BAD_REQUEST, "데일리 플랜 조회 기간이 올바르지 않습니다"),
    DAILY_PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "데일리 플랜을 찾을 수 없습니다"),
    DAILY_PLAN_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "데일리 플랜 항목을 찾을 수 없습니다"),
    INVALID_DAILY_PLAN_ITEM(HttpStatus.BAD_REQUEST, "데일리 플랜 항목이 올바르지 않습니다"),
    INVALID_DAILY_PLAN_ITEM_ORDER(HttpStatus.BAD_REQUEST, "데일리 플랜 항목 순서가 올바르지 않습니다"),
    INVALID_HOLIDAY_MONTH(HttpStatus.BAD_REQUEST, "공휴일 조회 연월이 올바르지 않습니다"),
    HOLIDAY_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "공휴일 정보를 아직 제공할 수 없습니다"),

    // SESSION DOMAIN
    DAILY_PLAN_TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "오늘의 계획에서 Task를 찾을 수 없습니다"),
    INVALID_SESSION_TASKS(HttpStatus.BAD_REQUEST, "세션 Task 목록이 올바르지 않습니다"),
    ACTIVE_SESSION_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 진행 중인 개인 세션이 있습니다"),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "세션을 찾을 수 없습니다"),
    SESSION_ALREADY_ENDED(HttpStatus.CONFLICT, "이미 종료된 세션입니다"),
    INVALID_SESSION_DURATION(HttpStatus.BAD_REQUEST, "세션 집중 시간이 올바르지 않습니다"),
    INVALID_MUSIC_URL(HttpStatus.BAD_REQUEST, "YouTube 음악 URL이 올바르지 않습니다"),

    // NOTE DOMAIN
    NOTE_NOT_FOUND(HttpStatus.NOT_FOUND, "노트를 찾을 수 없습니다"),
    INVALID_NOTE_CONTEXT(HttpStatus.BAD_REQUEST, "노트 컨텍스트가 올바르지 않습니다"),
    INVALID_TASK_ORGANIZER_SELECTION(HttpStatus.BAD_REQUEST, "할 일 정리 선택 항목이 원본 노트와 일치하지 않습니다"),
    INVALID_TASK_ORGANIZER_CONTEXT(HttpStatus.BAD_REQUEST, "할 일 정리 참조 범위가 올바르지 않습니다"),
    INVALID_TASK_ORGANIZER_RUN(HttpStatus.BAD_REQUEST, "할 일 정리 실행 기록이 올바르지 않습니다"),
    EMPTY_TASK_ORGANIZER_CONTEXT(HttpStatus.BAD_REQUEST, "참조할 폴더가 없습니다"),

    // PLACE DOMAIN
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "공간을 찾을 수 없습니다"),

    // KNOWLEDGE DOMAIN
    KNOWLEDGE_NODE_NOT_FOUND(HttpStatus.NOT_FOUND, "지식 노드를 찾을 수 없습니다"),
    KNOWLEDGE_NODE_TITLE_NOT_EDITABLE(HttpStatus.BAD_REQUEST, "카테고리와 목적 노드만 이름을 수정할 수 있습니다"),
    INVALID_KNOWLEDGE_FOLDER(HttpStatus.BAD_REQUEST, "지식 폴더가 아닙니다"),
    INVALID_KNOWLEDGE_RELATION(HttpStatus.BAD_REQUEST, "지식 관계의 연결 방향이 올바르지 않습니다"),
    INVALID_KNOWLEDGE_CURSOR(HttpStatus.BAD_REQUEST, "목록 조회 커서가 올바르지 않습니다"),
    INVALID_KNOWLEDGE_BRANCH(HttpStatus.BAD_REQUEST, "지식 구조의 연결이 올바르지 않습니다"),
    INVALID_GRAPH_DEPTH(HttpStatus.BAD_REQUEST, "그래프 확장 단계가 올바르지 않습니다"),
    KNOWLEDGE_TOPIC_NOT_DELETABLE(HttpStatus.CONFLICT, "목적은 문서를 삭제하면 함께 사라집니다"),
    KNOWLEDGE_CATEGORY_NOT_DELETABLE(HttpStatus.CONFLICT, "묶음은 폴더의 묶음 구성을 다시 정하면 사라집니다"),
    KNOWLEDGE_SOURCE_NOT_RETRYABLE(HttpStatus.CONFLICT, "본문 수집을 마치고 정리에 실패한 링크만 다시 분석할 수 있습니다"),
    KNOWLEDGE_CATEGORY_SOURCE_COUNT_INSUFFICIENT(
            HttpStatus.CONFLICT,
            "정리할 링크가 6개 이상 모이면 묶어 드릴 수 있습니다"
    ),
    INVALID_KNOWLEDGE_CATEGORY_ASSIGNMENT(HttpStatus.BAD_REQUEST, "묶음 구성이 올바르지 않습니다"),
    KNOWLEDGE_CATEGORY_PREVIEW_FAILURE(HttpStatus.BAD_GATEWAY, "링크를 묶는 데 실패했습니다"),

    // USER DOMAIN
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"),
    CURRENT_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다"),
    PASSWORD_REUSE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "현재 비밀번호와 다른 비밀번호를 입력해 주세요"),

    // SOURCE FAILURE CODES
    // Source의 개별 수집·소화 실패는 ProblemDetail이 아니라 failureMessage로 전달한다.
    // 프런트가 사용자 문구를 결정하므로 코드명 자체를 저장·전송한다.
    SOURCE_INVALID_URL(HttpStatus.UNPROCESSABLE_CONTENT, "SOURCE_INVALID_URL"),
    SOURCE_BLOCKED_ADDRESS(HttpStatus.UNPROCESSABLE_CONTENT, "SOURCE_BLOCKED_ADDRESS"),
    SOURCE_UNSUPPORTED_CONTENT_TYPE(HttpStatus.UNPROCESSABLE_CONTENT, "SOURCE_UNSUPPORTED_CONTENT_TYPE"),
    SOURCE_HTTP_ERROR(HttpStatus.BAD_GATEWAY, "SOURCE_HTTP_ERROR"),
    SOURCE_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "SOURCE_TIMEOUT"),
    SOURCE_UNKNOWN(HttpStatus.INTERNAL_SERVER_ERROR, "SOURCE_UNKNOWN"),
    SOURCE_EMPTY_CONTENT(HttpStatus.UNPROCESSABLE_CONTENT, "SOURCE_EMPTY_CONTENT"),
    SOURCE_DIGEST_FAILURE(HttpStatus.INTERNAL_SERVER_ERROR, "SOURCE_DIGEST_FAILURE"),
    SOURCE_DIGEST_NON_RETRYABLE_FAILURE(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "SOURCE_DIGEST_NON_RETRYABLE_FAILURE"
    ),
    SOURCE_DIGEST_RESOLUTION_FAILURE(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "SOURCE_DIGEST_RESOLUTION_FAILURE"
    ),
    SOURCE_DIGEST_RESOLUTION_NON_RETRYABLE_FAILURE(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "SOURCE_DIGEST_RESOLUTION_NON_RETRYABLE_FAILURE"
    );

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
