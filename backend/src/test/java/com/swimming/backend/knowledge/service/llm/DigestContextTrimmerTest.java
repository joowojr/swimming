package com.swimming.backend.knowledge.service.llm;

import com.swimming.backend.knowledge.config.LinkDigestProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DigestContextTrimmerTest {

    private static final int CAP = 10_000;
    private static final int BUDGET = 800;

    private final DigestContextTrimmer trimmer =
            new DigestContextTrimmer(new LinkDigestProperties(CAP, BUDGET));

    private String paragraph(int length) {
        return "가".repeat(length);
    }

    @Test
    @DisplayName("상한 안쪽이면 손대지 않는다")
    void keepsShortDocument() {
        String markdown = "# 제목\n\n" + paragraph(500);

        var result = trimmer.trim(markdown);

        assertThat(result.trimmed()).isFalse();
        assertThat(result.content()).isEqualTo(markdown);
    }

    @Test
    @DisplayName("헤딩은 하나도 빠뜨리지 않는다")
    void keepsEveryHeading() {
        StringBuilder markdown = new StringBuilder();
        for (int i = 1; i <= 30; i++) {
            markdown.append("## 구간 ").append(i).append("\n\n")
                    .append(paragraph(2000)).append("\n\n");
        }

        var result = trimmer.trim(markdown.toString());

        assertThat(result.trimmed()).isTrue();
        for (int i = 1; i <= 30; i++) {
            assertThat(result.content()).contains("## 구간 " + i);
        }
    }

    @Test
    @DisplayName("각 구간의 본문은 예산을 넘지 않는다")
    void limitsEachSectionToBudget() {
        String markdown = "## 하나\n\n" + paragraph(30_000)
                + "\n\n## 둘\n\n" + paragraph(30_000);

        String content = trimmer.trim(markdown).content();

        for (String section : content.split("(?m)^## ")) {
            assertThat(section.length()).isLessThanOrEqualTo(BUDGET + 40);
        }
    }

    @Test
    @DisplayName("첫 헤딩 앞의 도입부도 남긴다")
    void keepsPreamble() {
        String markdown = "이 문서는 무엇에 대한 글입니다. 도입부입니다.\n\n"
                + "## 첫 구간\n\n" + paragraph(20_000);

        String content = trimmer.trim(markdown).content();

        assertThat(content).startsWith("이 문서는 무엇에 대한 글입니다");
        assertThat(content).contains("## 첫 구간");
    }

    @Test
    @DisplayName("헤딩이 하나도 없으면 앞에서 상한까지만 자른다")
    void headTruncatesWhenNoHeading() {
        String markdown = paragraph(50_000);

        var result = trimmer.trim(markdown);

        assertThat(result.trimmed()).isTrue();
        assertThat(result.content().length()).isLessThanOrEqualTo(CAP);
        assertThat(result.content()).startsWith("가가가");
    }

    @Test
    @DisplayName("구간이 많으면 예산을 줄여서라도 헤딩을 전부 남긴다")
    void shrinksBudgetToKeepEveryHeading() {
        StringBuilder markdown = new StringBuilder();
        for (int i = 1; i <= 100; i++) {
            markdown.append("## 구간 ").append(i).append("\n\n")
                    .append(paragraph(3000)).append("\n\n");
        }

        var result = trimmer.trim(markdown.toString());

        assertThat(result.content().length()).isLessThanOrEqualTo(CAP);
        for (int i = 1; i <= 100; i++) {
            assertThat(result.content()).contains("## 구간 " + i);
        }
    }

    @Test
    @DisplayName("어떤 경우에도 상한을 넘기지 않는다")
    void neverExceedsCap() {
        StringBuilder markdown = new StringBuilder();
        for (int i = 1; i <= 200; i++) {
            markdown.append("### 구간 ").append(i).append("\n\n")
                    .append(paragraph(1000)).append("\n\n");
        }

        var result = trimmer.trim(markdown.toString());

        assertThat(result.content().length()).isLessThanOrEqualTo(CAP);
    }

    @Test
    @DisplayName("본문 없이 헤딩만 있는 구간도 헤딩을 남긴다")
    void keepsHeadingOnlySection() {
        String markdown = "## 빈 구간\n\n## 내용 있는 구간\n\n" + paragraph(30_000);

        String content = trimmer.trim(markdown).content();

        assertThat(content).contains("## 빈 구간");
        assertThat(content).contains("## 내용 있는 구간");
    }

    @Test
    @DisplayName("빈 문서는 빈 결과를 돌려준다")
    void handlesEmpty() {
        assertThat(trimmer.trim(null).content()).isEmpty();
        assertThat(trimmer.trim("   ").content()).isEmpty();
    }
}
