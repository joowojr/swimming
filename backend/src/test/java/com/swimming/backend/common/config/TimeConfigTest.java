package com.swimming.backend.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TimeConfigTest {

    @Test
    @DisplayName("시계는 DB와 같은 마이크로초 정밀도로 시각을 준다")
    void ticksInMicroseconds() {
        Clock clock = new TimeConfig().clock();

        // 리눅스는 나노초까지 주므로 플랫폼과 무관하게 잘라 냈는지 확인한다.
        assertThat(clock).isEqualTo(Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000)));
        assertThat(clock.instant().getNano() % 1_000).isZero();
    }
}
