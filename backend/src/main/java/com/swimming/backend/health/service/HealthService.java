package com.swimming.backend.health.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HealthService {

    private final JdbcTemplate jdbcTemplate;

    public void verifyDatabaseConnection() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (!Integer.valueOf(1).equals(result)) {
                throw new BusinessException(ErrorCode.DATABASE_UNAVAILABLE);
            }
        } catch (DataAccessException exception) {
            throw new BusinessException(ErrorCode.DATABASE_UNAVAILABLE, exception);
        }
    }
}
