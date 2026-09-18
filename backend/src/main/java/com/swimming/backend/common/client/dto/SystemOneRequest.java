package com.swimming.backend.common.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;
import java.util.Map;

/**
 * TypeSafe 평가 API 요청.
 * @param state 평가할 공통 문맥. JSON 문자열·객체·배열을 허용한다.
 * @param model 사용할 모델 ID 또는 별칭. 예: {@code jev-latest}.
 * @param questions 질문 ID별 질문 맵. 응답에도 같은 ID가 사용된다.
 */
public record SystemOneRequest(Object state, String model, Map<String, Question> questions) {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Choice.class, name = "choice"),
            @JsonSubTypes.Type(value = Score.class, name = "score"),
            @JsonSubTypes.Type(value = Noul.class, name = "noul")
    })
    /** JSON의 type 필드는 구현 타입에 따라 choice·score·noul로 생성된다. */
    public sealed interface Question permits Choice, Score, Noul {
        /** 평가 지시문. 문자열·객체·배열 또는 null을 허용한다. */
        Object instructions();
    }

    /**
     * 후보 중 하나를 선택하는 질문.
     * @param instructions 무엇을 선택할지 설명하는 지시문. 문자열·객체·배열 또는 null.
     * @param criteria 후보 ID와 설명의 맵. 설명은 문자열·객체·배열 또는 null이며,
     *                 선택된 후보 ID가 응답의 choice로 반환된다.
     */
    public record Choice(Object instructions, Map<String, ?> criteria) implements Question {}

    /**
     * 순서가 있는 기준으로 점수를 구하는 질문.
     * @param instructions 무엇을 평가할지 설명하는 지시문. 문자열·객체·배열 또는 null.
     * @param criteria 낮은 단계부터 나열한 기준 목록. 최소 두 단계가 필요하며,
     *                 설명은 문자열·객체·배열 또는 null이다. 인덱스 0부터 점수가 부여된다.
     */
    public record Score(Object instructions, List<?> criteria) implements Question {}

    /**
     * 예·아니요로 판단하는 질문.
     * @param instructions 판단할 질문 또는 명제. 문자열·객체·배열 또는 null.
     * @param criteria 선택적인 판단 기준. 키는 {@code "true"}·{@code "false"}이며,
     *                 값은 문자열·객체·배열 또는 null이다. 맵이 null이면 JSON에서 생략한다.
     */
    public record Noul(Object instructions,
                       @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, ?> criteria) implements Question {
        public Noul(Object instructions) {
            this(instructions, null);
        }
    }
}
