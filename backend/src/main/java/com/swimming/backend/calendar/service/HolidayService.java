package com.swimming.backend.calendar.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.calendar.dto.out.HolidayEvent;
import com.swimming.backend.calendar.domain.PublicHoliday;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class HolidayService {

    private static final long MAXIMUM_CACHED_MONTHS = 36;
    private static final Duration REFRESH_AFTER_WRITE = Duration.ofHours(24);
    private static final Duration EXPIRE_AFTER_WRITE = Duration.ofDays(7);

    private final HolidayProvider holidayProvider;
    private final LoadingCache<YearMonth, List<PublicHoliday>> holidaysByMonth;

    public HolidayService(HolidayProvider holidayProvider) {
        this.holidayProvider = holidayProvider;
        this.holidaysByMonth = Caffeine.newBuilder()
                .maximumSize(MAXIMUM_CACHED_MONTHS)
                .refreshAfterWrite(REFRESH_AFTER_WRITE)
                .expireAfterWrite(EXPIRE_AFTER_WRITE)
                .build(this::loadMonth);
    }

    public List<PublicHoliday> getHolidays(YearMonth month) {
        try {
            return holidaysByMonth.get(month);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.HOLIDAY_PROVIDER_UNAVAILABLE, exception);
        }
    }

    private List<PublicHoliday> loadMonth(YearMonth month) {
        List<HolidayEvent> events = holidayProvider.getHolidays(month);
        if (events == null) {
            throw new BusinessException(ErrorCode.HOLIDAY_PROVIDER_UNAVAILABLE);
        }

        Map<LocalDate, LinkedHashSet<String>> namesByDate = new TreeMap<>();
        for (HolidayEvent event : events) {
            if (event == null || event.date() == null || event.name() == null
                    || !YearMonth.from(event.date()).equals(month) || event.name().isBlank()) {
                throw new BusinessException(ErrorCode.HOLIDAY_PROVIDER_UNAVAILABLE);
            }
            namesByDate.computeIfAbsent(event.date(), ignored -> new LinkedHashSet<>())
                    .add(event.name().trim());
        }

        List<PublicHoliday> holidays = new ArrayList<>(namesByDate.size());
        namesByDate.forEach((date, names) -> holidays.add(new PublicHoliday(date, List.copyOf(names))));
        return List.copyOf(holidays);
    }
}
