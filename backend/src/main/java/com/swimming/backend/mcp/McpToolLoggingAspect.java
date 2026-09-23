package com.swimming.backend.mcp;

import com.swimming.backend.common.security.AuthUser;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
@Slf4j
public class McpToolLoggingAspect {

    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    public Object logToolCall(ProceedingJoinPoint joinPoint) throws Throwable {
        long startedAt = System.nanoTime();
        String toolName = resolveToolName(joinPoint);
        try {
            Object result = joinPoint.proceed();
            writeLog(toolName, "success", startedAt, null);
            return result;
        } catch (Throwable throwable) {
            writeLog(toolName, "failure", startedAt, throwable);
            throw throwable;
        }
    }

    private String resolveToolName(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Tool tool = method.getAnnotation(Tool.class);
        if (tool != null && !tool.name().isBlank()) {
            return tool.name();
        }
        return method.getName();
    }

    private void writeLog(String toolName, String status, long startedAt, Throwable throwable) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        log.info(
                "[SWIMMING-MCP] tool={} status={} duration_ms={} user_id={}{}",
                sanitize(toolName),
                status,
                durationMs,
                currentUserId(),
                throwable == null ? "" : " error_type=" + throwable.getClass().getSimpleName()
        );
    }

    private String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser authUser) {
            return authUser.id().toString();
        }
        return "anonymous";
    }

    private String sanitize(String value) {
        return value.replaceAll("[\\r\\n\\t]", "_");
    }
}
