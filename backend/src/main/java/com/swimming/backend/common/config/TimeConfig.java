package com.swimming.backend.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class TimeConfig {

    /**
     * DB의 timestamp는 마이크로초까지만 저장한다. 시계도 같은 정밀도로 맞춰
     * 저장 전 값과 다시 읽은 값이 어긋나지 않게 한다.
     *
     * <p>리눅스 시계는 나노초까지 주므로 맞추지 않으면 응답에 담은 시각과 DB의 시각이 달라진다.
     */
    @Bean
    public Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
