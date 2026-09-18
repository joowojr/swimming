package com.swimming.backend.common.config.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code app.typesafe}에 바인딩되는 API 연결 설정.
 * @param baseUrl API 기본 주소. 기본 설정은 {@code https://api.typesafe.ai}.
 * @param apiKey Bearer 인증 키. {@code TYPESAFE_API_KEY} 환경변수에서 읽는다.
 *               빈 값이면 API 호출 시 설정 오류를 알린다.
 * @param connectTimeout HTTP 연결 수립 제한 시간. 기본 설정은 3초.
 * @param responseTimeout HTTP 응답 읽기 제한 시간. 기본 설정은 30초.
 */
@ConfigurationProperties("app.typesafe")
public record TypeSafeProperties(String baseUrl, String apiKey, Duration connectTimeout, Duration responseTimeout) {}
