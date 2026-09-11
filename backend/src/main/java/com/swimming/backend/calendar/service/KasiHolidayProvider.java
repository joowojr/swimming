package com.swimming.backend.calendar.service;

import com.swimming.backend.calendar.config.KasiHolidayProperties;
import com.swimming.backend.calendar.dto.out.HolidayEvent;
import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
/** 한국천문연구원 특일 정보 API의 공휴일 조회 구현체. */
@Component
public class KasiHolidayProvider implements HolidayProvider {

    private static final DateTimeFormatter PROVIDER_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final KasiHolidayProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public KasiHolidayProvider(KasiHolidayProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public List<HolidayEvent> getHolidays(YearMonth month) {
        if (properties.serviceKey().isBlank()) {
            throw unavailable();
        }

        HttpRequest request = HttpRequest.newBuilder(requestUri(month))
                .timeout(properties.responseTimeout())
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            log.info(
                    "KASI response status={}, body={}",
                    response.statusCode(),
                    response.body()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw unavailable();
            }
            return parse(response.body());
        } catch (BusinessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable(exception);
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    private URI requestUri(YearMonth month) {
        String query = "ServiceKey=" + properties.serviceKey()
                + "&solYear=" + month.getYear()
                + "&solMonth=" + "%02d".formatted(month.getMonthValue())
                + "&_type=json"
                + "&numOfRows=100";
        return URI.create(properties.baseUrl() + "?" + query);
    }

    private List<HolidayEvent> parse(String responseBody) throws Exception {
        JsonNode response = objectMapper.readTree(responseBody)
                .path("response");

        JsonNode header = response.path("header");

        String resultCode = header.path("resultCode").asString();
        String resultMsg = header.path("resultMsg").asString();

        if (!"00".equals(resultCode)) {
            throw new IllegalStateException(
                    "KASI API error: resultCode=%s, resultMsg=%s"
                            .formatted(resultCode, resultMsg)
            );
        }

        JsonNode items = response.path("body")
                .path("items")
                .path("item");

        if (items.isMissingNode() || items.isNull()) {
            return List.of();
        }

        List<HolidayEvent> holidays = new ArrayList<>();

        if (items.isArray()) {
            items.forEach(item -> addHoliday(item, holidays));
        } else if (items.isObject()) {
            addHoliday(items, holidays);
        } else {
            throw unavailable();
        }

        return List.copyOf(holidays);
    }

    private static void addHoliday(JsonNode item, List<HolidayEvent> holidays) {
        if (!"Y".equals(item.path("isHoliday").asString())) {
            return;
        }

        String name = item.path("dateName").asString(null);
        String providerDate = item.path("locdate").asString(null);
        if (name == null || providerDate == null) {
            throw unavailable();
        }
        holidays.add(new HolidayEvent(LocalDate.parse(providerDate, PROVIDER_DATE), name));
    }

    private static BusinessException unavailable() {
        return new BusinessException(ErrorCode.HOLIDAY_PROVIDER_UNAVAILABLE);
    }

    private static BusinessException unavailable(Throwable cause) {
        return new BusinessException(ErrorCode.HOLIDAY_PROVIDER_UNAVAILABLE, cause);
    }
}
