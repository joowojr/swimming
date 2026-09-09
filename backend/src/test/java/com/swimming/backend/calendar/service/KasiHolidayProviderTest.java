package com.swimming.backend.calendar.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.swimming.backend.calendar.config.KasiHolidayProperties;
import com.swimming.backend.calendar.dto.out.HolidayEvent;
import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KasiHolidayProviderTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("한국천문연구원 응답에서 공공기관 휴일만 공급자 중립 이벤트로 변환한다")
    void convertsPublicHolidays() throws IOException {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        startServer(exchange -> {
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, 200, """
                    {
                      "response": {
                        "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
                        "body": {
                          "items": {"item": [
                            {"dateName": "추석", "isHoliday": "Y", "locdate": 20260925},
                            {"dateName": "기념일", "isHoliday": "N", "locdate": 20260928}
                          ]},
                          "totalCount": 2
                        }
                      }
                    }
                    """);
        });
        KasiHolidayProvider provider = provider("decoded+/=");

        List<HolidayEvent> result = provider.getHolidays(YearMonth.of(2026, 9));

        assertThat(result).containsExactly(new HolidayEvent(LocalDate.of(2026, 9, 25), "추석"));
        assertThat(rawQuery.get())
                .contains("ServiceKey=decoded%2B%2F%3D")
                .contains("solYear=2026")
                .contains("solMonth=09")
                .contains("_type=json")
                .contains("numOfRows=100");
    }

    @Test
    @DisplayName("정상 응답에 공휴일이 없으면 빈 목록을 반환한다")
    void returnsEmptyMonth() throws IOException {
        startServer(exchange -> respond(exchange, 200, """
                {
                  "response": {
                    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
                    "body": {"items": "", "totalCount": 0}
                  }
                }
                """));

        assertThat(provider("key").getHolidays(YearMonth.of(2026, 2))).isEmpty();
    }

    @Test
    @DisplayName("JSON 요청에 XML로 돌아온 공공데이터포털 오류도 공급자 장애로 변환한다")
    void convertsProviderError() throws IOException {
        startServer(exchange -> respond(exchange, 200, """
                <?xml version="1.0" encoding="UTF-8"?>
                <OpenAPI_ServiceResponse>
                  <cmmMsgHeader>
                    <errMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</errMsg>
                    <returnReasonCode>30</returnReasonCode>
                  </cmmMsgHeader>
                </OpenAPI_ServiceResponse>
                """));

        assertUnavailable(() -> provider("invalid-key").getHolidays(YearMonth.of(2026, 9)));
    }

    @Test
    @DisplayName("서비스 키가 없으면 외부 API를 호출하지 않는다")
    void rejectsMissingKeyWithoutRequest() throws IOException {
        AtomicInteger requestCount = new AtomicInteger();
        startServer(exchange -> {
            requestCount.incrementAndGet();
            respond(exchange, 200, "");
        });

        assertUnavailable(() -> provider(" ").getHolidays(YearMonth.of(2026, 9)));
        assertThat(requestCount).hasValue(0);
    }

    @Test
    @DisplayName("읽을 수 없는 JSON을 공급자 장애로 변환한다")
    void rejectsMalformedJson() throws IOException {
        startServer(exchange -> respond(exchange, 200, "{\"response\":"));

        assertUnavailable(() -> provider("key").getHolidays(YearMonth.of(2026, 9)));
    }

    private KasiHolidayProvider provider(String serviceKey) {
        return new KasiHolidayProvider(
                new KasiHolidayProperties(
                        "http://127.0.0.1:" + server.getAddress().getPort() + "/holidays",
                        serviceKey,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2)
                ),
                new ObjectMapper()
        );
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/holidays", exchange -> handler.handle(exchange));
        server.start();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void assertUnavailable(ThrowingCall call) {
        assertThatThrownBy(call::invoke)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.HOLIDAY_PROVIDER_UNAVAILABLE));
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void invoke();
    }
}
