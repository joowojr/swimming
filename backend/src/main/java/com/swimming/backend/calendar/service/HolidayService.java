package com.swimming.backend.calendar.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.calendar.dto.out.HolidayEvent;
import com.swimming.backend.calendar.domain.PublicHoliday;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Service
public class HolidayService {

    // 한국 공휴일 데이터이므로 서버 기본 타임존이 아닌 한국 시간 기준으로 현재 월을 판단한다.
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    // 과거 월은 변경 가능성이 매우 낮아 넉넉하게 보관한다.
    private static final long MAXIMUM_PAST_MONTHS = 60;

    // 현재 월 캐시는 사실상 하나의 월만 사용하지만,
    // 월 전환 시점의 이전 엔트리까지 고려해 여유 있게 2개까지 보관한다.
    private static final long MAXIMUM_CURRENT_MONTHS = 2;

    // 미래 공휴일 조회 범위를 고려해 최대 36개월까지 보관한다.
    private static final long MAXIMUM_FUTURE_MONTHS = 36;

    // 이미 지난 공휴일은 변경 가능성이 매우 낮으므로 장기간 캐시한다.
    private static final Duration PAST_EXPIRE_AFTER_WRITE = Duration.ofDays(180);

    // 현재 월은 임시공휴일 등 변경 가능성을 고려해 비교적 짧게 유지한다.
    private static final Duration CURRENT_EXPIRE_AFTER_WRITE = Duration.ofDays(7);

    // 미래 월은 변경될 수 있지만 실시간 갱신이 필요하지 않아 중간 수준의 TTL을 사용한다.
    private static final Duration FUTURE_EXPIRE_AFTER_WRITE = Duration.ofDays(30);

    private final HolidayProvider holidayProvider;

    // 공휴일은 사용자별 데이터가 아니므로 모든 사용자 요청이 동일한 월별 캐시를 공유한다.
    private final LoadingCache<YearMonth, List<PublicHoliday>> pastHolidays;
    private final LoadingCache<YearMonth, List<PublicHoliday>> currentHolidays;
    private final LoadingCache<YearMonth, List<PublicHoliday>> futureHolidays;

    public HolidayService(HolidayProvider holidayProvider) {
        this.holidayProvider = holidayProvider;

        this.pastHolidays = Caffeine.newBuilder()
                .maximumSize(MAXIMUM_PAST_MONTHS)
                .expireAfterWrite(PAST_EXPIRE_AFTER_WRITE)
                .recordStats()
                .build(this::loadMonth);

        this.currentHolidays = Caffeine.newBuilder()
                .maximumSize(MAXIMUM_CURRENT_MONTHS)
                .expireAfterWrite(CURRENT_EXPIRE_AFTER_WRITE)
                .recordStats()
                .build(this::loadMonth);

        this.futureHolidays = Caffeine.newBuilder()
                .maximumSize(MAXIMUM_FUTURE_MONTHS)
                .expireAfterWrite(FUTURE_EXPIRE_AFTER_WRITE)
                .recordStats()
                .build(this::loadMonth);
    }

    public List<PublicHoliday> getHolidays(YearMonth month) {
        LoadingCache<YearMonth, List<PublicHoliday>> cache = resolveCache(month);

        // getIfPresent로 먼저 확인해 현재 요청이 hit인지 miss인지 명확하게 구분한다.
        List<PublicHoliday> cached = cache.getIfPresent(month);

        if (cached != null) {
            log.debug("Holiday cache hit. month={}", month);
            return cached;
        }

        log.debug("Holiday cache miss. month={}", month);

        try {
            return cache.get(month);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    ErrorCode.HOLIDAY_PROVIDER_UNAVAILABLE,
                    exception
            );
        }
    }

    /**
     * 요청 월을 현재 한국 시간 기준으로 과거/현재/미래로 구분한다.
     *
     * 월이 바뀌면 동일한 YearMonth라도 다음 요청부터 자동으로 다른 캐시를 사용한다.
     * 예를 들어 9월에는 10월이 future 캐시에 저장되지만,
     * 10월이 된 이후의 요청은 current 캐시를 사용한다.
     */
    private LoadingCache<YearMonth, List<PublicHoliday>> resolveCache(YearMonth month) {
        YearMonth currentMonth = YearMonth.now(KOREA_ZONE);

        if (month.isBefore(currentMonth)) {
            return pastHolidays;
        }

        if (month.equals(currentMonth)) {
            return currentHolidays;
        }

        return futureHolidays;
    }

    /**
     * 캐시 miss 발생 시 외부 공휴일 Provider에서 해당 월 데이터를 조회한다.
     *
     * 동일 날짜에 여러 공휴일명이 존재할 수 있으므로 날짜별로 이름을 집계하며,
     * LinkedHashSet을 사용해 중복 이름을 제거하면서 Provider의 순서를 유지한다.
     */
    private List<PublicHoliday> loadMonth(YearMonth month) {
        List<HolidayEvent> events = holidayProvider.getHolidays(month);

        if (events == null) {
            throw new BusinessException(ErrorCode.HOLIDAY_PROVIDER_UNAVAILABLE);
        }

        // 날짜 순으로 반환하기 위해 TreeMap을 사용한다.
        Map<LocalDate, LinkedHashSet<String>> namesByDate = new TreeMap<>();

        for (HolidayEvent event : events) {
            // 외부 Provider 응답이 요청 월과 다르거나 필수 값이 누락된 경우
            // 잘못된 데이터를 캐시에 저장하지 않고 조회 실패로 처리한다.
            if (event == null
                    || event.date() == null
                    || event.name() == null
                    || !YearMonth.from(event.date()).equals(month)
                    || event.name().isBlank()) {
                throw new BusinessException(ErrorCode.HOLIDAY_PROVIDER_UNAVAILABLE);
            }

            namesByDate
                    .computeIfAbsent(event.date(), ignored -> new LinkedHashSet<>())
                    .add(event.name().trim());
        }

        List<PublicHoliday> holidays = new ArrayList<>(namesByDate.size());

        namesByDate.forEach((date, names) ->
                holidays.add(
                        new PublicHoliday(date, List.copyOf(names))
                )
        );

        // 캐시 내부 값이 외부에서 수정되지 않도록 불변 리스트로 반환한다.
        return List.copyOf(holidays);
    }
}