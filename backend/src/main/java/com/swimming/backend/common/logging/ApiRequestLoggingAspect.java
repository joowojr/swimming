package com.swimming.backend.common.logging;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
@Slf4j
public class ApiRequestLoggingAspect {

    private static final int MAX_QUERY_LENGTH = 1000;
    private static final String ANONYMOUS_USER = "anonymous";
    private static final int MAX_CAUSE_DEPTH = 5;

    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object logApiRequest(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes = currentRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        if (!isApiRequest(request)) {
            return joinPoint.proceed();
        }
        HttpServletResponse response = attributes.getResponse();
        long startedAt = System.nanoTime();

        try {
            Object result = joinPoint.proceed();
            writeLog(
                    request,
                    resolveSuccessStatus(result, response),
                    startedAt,
                    null
            );
            return result;
        } catch (Throwable throwable) {
            writeLog(
                    request,
                    resolveFailureStatus(throwable, response),
                    startedAt,
                    throwable
            );
            throw throwable;
        }
    }

    private ServletRequestAttributes currentRequestAttributes() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {
            return attributes;
        }
        return null;
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/api".equals(path) || path.startsWith("/api/");
    }

    private int resolveSuccessStatus(Object result, HttpServletResponse response) {
        if (result instanceof ResponseEntity<?> responseEntity) {
            return responseEntity.getStatusCode().value();
        }
        return response == null ? HttpServletResponse.SC_OK : response.getStatus();
    }

    private int resolveFailureStatus(
            Throwable throwable,
            HttpServletResponse response
    ) {
        if (throwable instanceof BusinessException businessException) {
            return businessException.getErrorCode().getStatus().value();
        }
        if (response != null && response.getStatus() >= 400) {
            return response.getStatus();
        }
        return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }

    private void writeLog(
            HttpServletRequest request,
            int status,
            long startedAt,
            Throwable throwable
    ) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAt
        );
        log.info(
                "[SWIMMING_API] method={} path={} query=\"{}\" status={} duration_ms={} user_id={}{}",
                sanitizeLogValue(request.getMethod()),
                sanitizeLogValue(request.getRequestURI()),
                sanitizeQuery(request.getQueryString()),
                status,
                durationMs,
                currentUserId(),
                failureSuffix(throwable)
        );
    }

    /**
     * 실패한 요청에만 원인을 덧붙인다.
     *
     * <p>status만으로는 같은 503이 키 누락인지 timeout인지 구분되지 않는다. ErrorCode 이름과
     * 예외 타입 사슬을 남겨 그 구분을 만든다.
     *
     * <p>예외 <b>메시지</b>는 남기지 않는다. 외부 호출이 던진 예외의 메시지에는 요청 URI가
     * 통째로 들어 있을 수 있고, 그 URI에 공급자 인증키가 붙어 있다. 타입 이름만으로도
     * timeout·인증 실패·JSON 오류는 갈린다.
     */
    static String failureSuffix(Throwable throwable) {
        if (throwable == null) {
            return "";
        }

        StringBuilder suffix = new StringBuilder();
        if (throwable instanceof BusinessException businessException) {
            suffix.append(" error_code=")
                    .append(businessException.getErrorCode().name());
        }
        return suffix.append(" cause=").append(causeChain(throwable)).toString();
    }

    private static String causeChain(Throwable throwable) {
        StringBuilder chain = new StringBuilder();
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (!chain.isEmpty()) {
                chain.append(" <- ");
            }
            chain.append(current.getClass().getName());
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return chain.toString();
    }

    private String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext()
                .getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser authUser) {
            return authUser.id().toString();
        }
        return ANONYMOUS_USER;
    }

    static String sanitizeQuery(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return "-";
        }

        String[] parameters = queryString.split("&");
        StringBuilder sanitized = new StringBuilder();
        for (String parameter : parameters) {
            if (!sanitized.isEmpty()) {
                sanitized.append('&');
            }

            int separator = parameter.indexOf('=');
            String key = separator >= 0 ? parameter.substring(0, separator) : parameter;
            if (isSensitiveKey(key)) {
                sanitized.append(sanitizeLogValue(key)).append("=***");
            } else {
                sanitized.append(sanitizeLogValue(parameter));
            }
        }

        if (sanitized.length() > MAX_QUERY_LENGTH) {
            return sanitized.substring(0, MAX_QUERY_LENGTH) + "...";
        }
        return sanitized.toString();
    }

    private static boolean isSensitiveKey(String encodedKey) {
        String key;
        try {
            key = URLDecoder.decode(encodedKey, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            key = encodedKey;
        }

        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.contains("password")
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("authorization")
                || normalized.contains("api_key")
                || normalized.contains("apikey")
                || normalized.equals("code")
                || normalized.endsWith("_code");
    }

    private static String sanitizeLogValue(String value) {
        if (value == null) {
            return "-";
        }
        return value.replace('\r', '_').replace('\n', '_');
    }
}
