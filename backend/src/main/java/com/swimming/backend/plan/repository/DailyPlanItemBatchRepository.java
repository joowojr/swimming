package com.swimming.backend.plan.repository;

import com.swimming.backend.plan.domain.DailyPlanItem;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class DailyPlanItemBatchRepository {

    private static final String INSERT_PREFIX = """
            INSERT INTO daily_plan_items (
                user_id, plan_date, task_id, order_idx, created_at, updated_at
            ) VALUES
            """;
    private static final String VALUE_PLACEHOLDERS = "(?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

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
                statement.setInt(parameterIndex++, item.getOrderIdx());
            }
            return statement;
        });
    }

    public int updateOrders(
            Long userId,
            LocalDate planDate,
            Map<Long, Integer> orderIdxByItemId
    ) {
        if (orderIdxByItemId.isEmpty()) {
            return 0;
        }

        String cases = String.join(" ", orderIdxByItemId.keySet().stream()
                .map(itemId -> "WHEN ? THEN ?")
                .toList());
        String idPlaceholders = String.join(", ", orderIdxByItemId.keySet().stream()
                .map(itemId -> "?")
                .toList());
        String sql = """
                UPDATE daily_plan_items
                SET order_idx = CASE id %s END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                  AND plan_date = ?
                  AND id IN (%s)
                """.formatted(cases, idPlaceholders);

        return jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(sql);
            int parameterIndex = 1;
            for (var entry : orderIdxByItemId.entrySet()) {
                statement.setLong(parameterIndex++, entry.getKey());
                statement.setInt(parameterIndex++, entry.getValue());
            }
            statement.setLong(parameterIndex++, userId);
            statement.setDate(parameterIndex++, Date.valueOf(planDate));
            for (Long itemId : orderIdxByItemId.keySet()) {
                statement.setLong(parameterIndex++, itemId);
            }
            return statement;
        });
    }
}
