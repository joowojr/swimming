package com.swimming.backend.common.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Map;

/**
 * TypeSafe 평가 API 응답.
 * @param model 실제 평가를 수행한 모델.
 * @param answers 요청의 질문 ID별 답변 맵.
 * @param usage 이번 요청의 입력·출력 토큰 사용량.
 */
public record SystemOneResponse(String model, Map<String, Answer> answers, Usage usage) {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Choice.class, name = "choice"),
            @JsonSubTypes.Type(value = Score.class, name = "score"),
            @JsonSubTypes.Type(value = Noul.class, name = "noul")
    })
    /** JSON의 type 필드로 choice·score·noul 답변 타입을 구분한다. */
    public sealed interface Answer permits Choice, Score, Noul {}

    /**
     * 후보 선택 결과.
     * @param choice 확률이 가장 높은 후보 ID. 요청의 criteria 키 중 하나.
     * @param probabilities 후보 ID별 확률. 값은 0~1이며 합계는 1이다.
     * @param confidence 확률 분포에서 도출한 확신도(0~1). 선택 후보의 확률과는 다르다.
     */
    public record Choice(String choice, Map<String, Double> probabilities, double confidence) implements Answer {}

    /**
     * 단계별 평가 결과.
     * @param score 단계 인덱스를 확률로 가중 평균한 점수. 정수가 아닌 중간값도 반환된다.
     * @param legend 문자열로 된 단계 인덱스와 요청 기준 설명의 맵. 예: {@code "0"}.
     * @param probabilities 문자열로 된 단계 인덱스별 확률. 값은 0~1이며 합계는 1이다.
     * @param confidence 확률 분포에서 도출한 확신도(0~1).
     */
    public record Score(double score, Map<String, Object> legend,
                        Map<String, Double> probabilities, double confidence) implements Answer {}

    /**
     * 예·아니요 판단 결과.
     * @param noul 답이 '예'일 확률(0~1). 0에 가까우면 아니요, 1에 가까우면 예를 의미한다.
     */
    public record Noul(double noul) implements Answer {}

    /**
     * 요청별 토큰 사용량.
     * @param inputTokens 입력 토큰 수. JSON 필드명은 {@code input_tokens}.
     * @param outputTokens 출력 토큰 수. JSON 필드명은 {@code output_tokens}.
     */
    public record Usage(@JsonProperty("input_tokens") long inputTokens,
                        @JsonProperty("output_tokens") long outputTokens) {}
}
