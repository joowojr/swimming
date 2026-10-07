package com.swimming.backend.common.exception;

import com.swimming.backend.knowledge.exception.CategoryTitleDuplicateException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CategoryTitleDuplicateException.class)
    public ProblemDetail handleCategoryTitleDuplicate(CategoryTitleDuplicateException exception) {
        ProblemDetail problemDetail = handleBusiness(exception);
        problemDetail.setProperty("targetCategory", exception.getTargetCategory());
        return problemDetail;
    }

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                exception.getStatus(), exception.getMessage());
        problemDetail.setProperty("code", exception.getCode());
        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다");
        problemDetail.setProperty("errors", toValidationErrors(exception));
        return problemDetail;
    }

    private Map<String, String> toValidationErrors(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return errors;
    }
}
