package com.swimming.backend.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {

    private final Enum<?> errorCode;
    private final HttpStatus status;
    private final String code;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getStatus(), errorCode.getMessage(), errorCode.name(), null);
    }

    public BusinessException(com.swimming.backend.agentwork.domain.AgentWorkErrorCode errorCode) {
        this(errorCode, errorCode.getStatus(), errorCode.getMessage(), errorCode.name(), null);
    }

    private BusinessException(Enum<?> errorCode, HttpStatus status, String message, String code, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.status = status;
        this.code = code;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        this(errorCode, errorCode.getStatus(), errorCode.getMessage(), errorCode.name(), cause);
    }

    public BusinessException(com.swimming.backend.agentwork.domain.AgentWorkErrorCode errorCode, Throwable cause) {
        this(errorCode, errorCode.getStatus(), errorCode.getMessage(), errorCode.name(), cause);
    }

    public Enum<?> getErrorCode() { return errorCode; }
    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }

}
