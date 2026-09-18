package com.swimming.backend.calendar.repository;

import com.swimming.backend.calendar.domain.DailyPlanItem;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class DailyPlanItemBatchRepository {

    private static final String INSERT_PREFIX = """
            INSERT INTO daily_plan_items (
                user_id, plan_date, task_id, created_at, updated_at
            ) VALUES
            """;
    private static final String VALUE_PLACEHOLDERS = "(?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

    private final JdbcTemplate jdbcTemplate;

    public void insertAll(Long userId, LocalDate planDate, List<DailyPlanItem> items) {
        if (items.isEmpty()) {
            return;
        }

        String sql = INSERT_PREFIX + String.join(", ", items.stream()
                .map(item -> VALUE_PLACEHOLDERS)
                .toList());

        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(sql);
            int parameterIndex = 1;
            for (DailyPlanItem item : items) {
                statement.setLong(parameterIndex++, userId);
                statement.setDate(parameterIndex++, Date.valueOf(planDate));
                statement.setLong(parameterIndex++, item.getTaskId());
            }
            return statement;
        });
    }

}
