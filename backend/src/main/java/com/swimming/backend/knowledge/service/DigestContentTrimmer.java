package com.swimming.backend.knowledge.service;

import com.swimming.backend.knowledge.config.KnowledgeDigestProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 긴 문서를 LLM에에 넣을 분량으로 줄인다.
 *
 * <p>앞에서 통째로 자르면 뒤쪽 개념을 잃는다. Spring Boot Externalized Configuration을
 * 앞에서 80,000자로 자르면 헤딩 44개 중 30개만 남고, 사라지는 것이 {@code Relaxed Binding},
 * {@code Properties Conversion} 같은 실제 Subject 후보였다.
 *
 * <p>그래서 헤딩은 모두 남기고 각 구간의 앞부분만 가져간다. 헤딩은 그 자체로 Subject
 * 후보 목록이라, 같은 분량이면 이쪽이 훨씬 많은 개념을 담는다.
 */
@Component
@RequiredArgsConstructor
public class DigestContentTrimmer {

    private static final Pattern HEADING = Pattern.compile("^#{1,6} .*");

    /** 줄 경계에서 끊을지 판단하는 기준. 자른 위치가 이 비율보다 앞이면 그냥 그 자리에서 끊는다. */
    private static final double LINE_BREAK_TOLERANCE = 0.8;

    private final KnowledgeDigestProperties properties;

    public Result trim(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return new Result("", false);
        }

        int cap = properties.maxInputLength();

        // 상한 안쪽이면 손대지 않는다. 대부분의 문서가 여기서 끝난다.
        if (markdown.length() <= cap) {
            return new Result(markdown, false);
        }

        List<Section> sections = split(markdown);

        // 헤딩이 하나도 없으면 나눌 기준이 없다. 앞에서 자르는 수밖에 없다.
        if (sections.stream().noneMatch(Section::hasHeading)) {
            return new Result(cut(markdown, cap), true);
        }

        return new Result(cut(applyBudget(sections, cap), cap), true);
    }

    /**
     * 헤딩을 먼저 확보하고 남은 자리를 구간들이 나눠 갖는다.
     *
     * <p>예산을 상한과 무관하게 정하면 마지막에 상한으로 다시 잘리면서 뒤쪽 헤딩이 통째로
     * 사라진다. 헤딩을 다 남기는 것이 이 방식의 목적이므로 예산을 상한에서 역산한다.
     */
    private String applyBudget(List<Section> sections, int cap) {
        int headingsLength = sections.stream()
                .filter(Section::hasHeading)
                .mapToInt(section -> section.heading().length() + 1)
                .sum();

        int available = cap - headingsLength;
        int bodyBudget = available <= 0
                ? 0
                : Math.min(properties.sectionBudget(), available / sections.size());

        StringBuilder trimmed = new StringBuilder();

        for (Section section : sections) {
            String heading = section.heading();
            String body = section.body().strip();

            if (heading == null && body.isEmpty()) {
                continue;
            }

            if (heading != null) {
                trimmed.append(heading).append('\n');
            }

            // 헤딩은 온전히 남기고 본문만 예산으로 제한한다.
            if (!body.isEmpty() && bodyBudget > 0) {
                trimmed.append(cut(body, bodyBudget)).append('\n');
            }

            trimmed.append('\n');
        }

        return trimmed.toString().strip();
    }

    /**
     * 첫 헤딩 앞의 도입부도 하나의 구간으로 본다. 요약은 대개 거기서 나온다.
     */
    private List<Section> split(String markdown) {
        List<Section> sections = new ArrayList<>();
        String heading = null;
        StringBuilder body = new StringBuilder();

        for (String line : markdown.split("\n", -1)) {
            if (HEADING.matcher(line).matches()) {
                sections.add(new Section(heading, body.toString()));
                heading = line;
                body.setLength(0);
            } else {
                body.append(line).append('\n');
            }
        }
        sections.add(new Section(heading, body.toString()));

        return sections;
    }

    /** 문장 중간에서 끊기지 않도록 줄 경계를 찾는다. */
    private String cut(String text, int limit) {
        if (text.length() <= limit) {
            return text;
        }

        String cut = text.substring(0, limit);
        int lastBreak = cut.lastIndexOf('\n');

        if (lastBreak > limit * LINE_BREAK_TOLERANCE) {
            cut = cut.substring(0, lastBreak);
        }

        return cut.strip();
    }

    private record Section(String heading, String body) {
        boolean hasHeading() {
            return heading != null;
        }
    }

    /**
     * @param trimmed 분량을 줄였는지. 줄이지 않았으면 문서 전체가 들어간 것이다.
     */
    public record Result(String content, boolean trimmed) {
    }
}
