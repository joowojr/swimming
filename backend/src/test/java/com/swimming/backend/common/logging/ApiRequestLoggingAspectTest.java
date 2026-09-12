package com.swimming.backend.common.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.common.security.AuthUser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiRequestLoggingAspectTest {

    private ApiRequestLoggingAspect aspect;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        aspect = new ApiRequestLoggingAspect();
        logger = (Logger) LoggerFactory.getLogger(ApiRequestLoggingAspect.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("API 요청의 경로와 Query와 응답 상태와 사용자 ID를 기록한다")
    void logsSuccessfulApiRequest() throws Throwable {
        MockHttpServletRequest request = request(
                "GET",
                "/api/daily-plan",
                "from_date=2026-08-20&to_date=2026-08-26"
        );
        bindRequest(request);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(
                        new AuthUser(7L, "user@example.com"),
                        null
                )
        );
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn(
                ResponseEntity.status(HttpStatus.CREATED).build()
        );

        Object result = aspect.logApiRequest(joinPoint);

        assertThat(result).isInstanceOf(ResponseEntity.class);
        assertThat(lastLog())
                .contains("[SWIMMING_API] method=GET")
                .contains("path=/api/daily-plan")
                .contains("query=\"from_date=2026-08-20&to_date=2026-08-26\"")
                .contains("status=201")
                .contains("duration_ms=")
                .contains("user_id=7");
    }

    @Test
    @DisplayName("비즈니스 예외 요청의 상태와 비인증 사용자를 기록하고 예외를 다시 던진다")
    void logsBusinessExceptionAndRethrows() throws Throwable {
        bindRequest(request("POST", "/api/sessions", null));
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        BusinessException exception = new BusinessException(
                ErrorCode.ACTIVE_SESSION_ALREADY_EXISTS
        );
        when(joinPoint.proceed()).thenThrow(exception);

        assertThatThrownBy(() -> aspect.logApiRequest(joinPoint))
                .isSameAs(exception);
        assertThat(lastLog())
                .contains("method=POST")
                .contains("path=/api/sessions")
                .contains("query=\"-\"")
                .contains("status=409")
                .contains("user_id=anonymous");
    }

    @Test
    @DisplayName("실패한 요청은 ErrorCode 이름과 예외 타입 사슬을 함께 기록한다")
    void 실패는_원인을_함께_남긴다() throws Throwable {
        bindRequest(request("GET", "/api/calendar/holiday", "year=2026&month=9"));
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        BusinessException exception = new BusinessException(
                ErrorCode.HOLIDAY_PROVIDER_UNAVAILABLE,
                new java.net.SocketTimeoutException("보이면 안 되는 메시지")
        );
        when(joinPoint.proceed()).thenThrow(exception);

        assertThatThrownBy(() -> aspect.logApiRequest(joinPoint))
                .isSameAs(exception);
        assertThat(lastLog())
                .contains("status=503")
                .contains("error_code=HOLIDAY_PROVIDER_UNAVAILABLE")
                .contains("cause=com.swimming.backend.common.exception.BusinessException")
                .contains("<- java.net.SocketTimeoutException");
    }

    @Test
    @DisplayName("예외 메시지는 남기지 않는다. 외부 호출 예외에 인증키가 붙은 URI가 실릴 수 있다")
    void 예외_메시지는_남기지_않는다() {
        String suffix = ApiRequestLoggingAspect.failureSuffix(
                new IllegalStateException("ServiceKey=SECRET%2Bvalue")
        );

        assertThat(suffix).doesNotContain("SECRET");
        assertThat(suffix).contains("java.lang.IllegalStateException");
    }

    @Test
    @DisplayName("성공한 요청에는 원인을 붙이지 않는다")
    void 성공에는_원인을_붙이지_않는다() {
        assertThat(ApiRequestLoggingAspect.failureSuffix(null)).isEmpty();
    }

    @Test
    @DisplayName("민감한 Query 값은 마스킹하고 로그 개행을 제거한다")
    void masksSensitiveQueryValues() {
        assertThat(ApiRequestLoggingAspect.sanitizeQuery(
                "from_date=2026-08-20&access_token=secret\nvalue&code=oauth-code"
        )).isEqualTo(
                "from_date=2026-08-20&access_token=***&code=***"
        );
    }

    @Test
    @DisplayName("API가 아닌 Controller 요청은 기록하지 않는다")
    void skipsNonApiRequest() throws Throwable {
        bindRequest(request("GET", "/internal/status", null));
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn(ResponseEntity.ok().build());

        aspect.logApiRequest(joinPoint);

        verify(joinPoint).proceed();
        assertThat(appender.list).isEmpty();
    }

    private MockHttpServletRequest request(
            String method,
            String path,
            String queryString
    ) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setQueryString(queryString);
        return request;
    }

    private void bindRequest(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                request,
                new MockHttpServletResponse()
        ));
    }

    private String lastLog() {
        return appender.list.getLast().getFormattedMessage();
    }
}
