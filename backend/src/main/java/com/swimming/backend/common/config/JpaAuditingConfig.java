package com.swimming.backend.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
@RequiredArgsConstructor
public class JpaAuditingConfig {

    /**
     * created_at·updated_at도 서비스와 같은 시계를 쓴다.
     *
     * <p>기본 제공자는 시스템 시계를 직접 읽어 DB보다 정밀한 시각을 만든다. 그러면 저장 전 엔티티의
     * created_at과 다시 읽은 값이 달라지고, createdAt을 담는 커서 페이지네이션이 행을 건너뛰거나 겹칠 수 있다.
     */
    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }
}
