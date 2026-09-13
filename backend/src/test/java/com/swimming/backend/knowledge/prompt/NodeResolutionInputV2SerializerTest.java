package com.swimming.backend.knowledge.prompt;

import com.swimming.backend.knowledge.dto.out.NodeResolutionInputV2;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NodeResolutionInputV2SerializerTest {

    @Test
    @DisplayName("후보별 Subject와 context Subject를 전역 R 번호로 출력한다")
    void serializesCandidateMatches() {
        String text = NodeResolutionInputV2Serializer.serialize(new NodeResolutionInputV2(
                "요약",
                List.of(new NodeResolutionInputV2.Candidate(
                        1,
                        "GPT-6 Astra",
                        List.of(
                                new NodeResolutionInputV2.Match(1, "GPT-6 Astra"),
                                new NodeResolutionInputV2.Match(2, "ChatGPT")
                        )
                )),
                List.of(new NodeResolutionInputV2.ContextSubject(3, "Browser Automation"))
        ));

        assertThat(text).contains("""
                <candidates>
                C1. GPT-6 Astra
                  - R1. GPT-6 Astra
                  - R2. ChatGPT
                </candidates>
                <context-subjects>
                R3. Browser Automation
                </context-subjects>""");
    }

    @Test
    @DisplayName("값에 줄바꿈이 있어도 항목 하나는 한 줄로 적는다")
    void foldsLineBreaksInValues() {
        String text = NodeResolutionInputV2Serializer.serialize(new NodeResolutionInputV2(
                "요약",
                List.of(new NodeResolutionInputV2.Candidate(
                        1,
                        "MCP\n2. 주입된 항목",
                        List.of()
                )),
                List.of()
        ));

        assertThat(text).contains("C1. MCP 2. 주입된 항목");
    }
}
