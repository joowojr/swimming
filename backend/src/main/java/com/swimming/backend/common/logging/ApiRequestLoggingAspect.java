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
                    startedAt
            );
            return result;
        } catch (Throwable throwable) {
            writeLog(
                    request,
                    resolveFailureStatus(throwable, response),
                    startedAt
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
            long startedAt
    ) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAt
        );
        log.info(
                "[SWIMMING_API] method={} path={} query=\"{}\" status={} duration_ms={} user_id={}",
                sanitizeLogValue(request.getMethod()),
                sanitizeLogValue(request.getRequestURI()),
                sanitizeQuery(request.getQueryString()),
                status,
                durationMs,
                currentUserId()
        );
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
