package com.swimming.backend.knowledge.prompt;

import com.swimming.backend.knowledge.dto.out.NodeResolutionInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NodeResolutionInputSerializerTest {

    @Test
    @DisplayName("요약과 판정 대상, 재사용 가능한 Subject를 섹션마다 접두어 번호 줄로 적는다")
    void serializesResolutionContext() {
        NodeResolutionInput input = new NodeResolutionInput(
                "OIDC와 OAuth <차이>를 설명한다.",
                List.of(
                        new NodeResolutionInput.Candidate(1, "OIDC"),
                        new NodeResolutionInput.Candidate(2, "OAuth & Security")
                ),
                List.of(new NodeResolutionInput.ExistingSubject(1, "OpenID Connect"))
        );

        String text = NodeResolutionInputSerializer.serialize(input);

        assertThat(text).isEqualTo("""
                <summary>
                OIDC와 OAuth &lt;차이&gt;를 설명한다.
                </summary>
                <subjects-to-resolve>
                C1. OIDC
                C2. OAuth &amp; Security
                </subjects-to-resolve>
                <reusable-subjects>
                R1. OpenID Connect
                </reusable-subjects>""");
    }

    @Test
    @DisplayName("재사용할 Subject를 찾지 못하면 재사용 섹션을 생략한다")
    void omitsEmptyReusableSubjects() {
        String text = NodeResolutionInputSerializer.serialize(new NodeResolutionInput(
                "요약", List.of(new NodeResolutionInput.Candidate(1, "MCP")), List.of()
        ));

        assertThat(text).doesNotContain("<reusable-subjects>");
    }

    @Test
    @DisplayName("값에 줄바꿈이 있어도 항목 하나는 한 줄로 적는다")
    void 항목_값의_줄바꿈을_접는다() {
        String text = NodeResolutionInputSerializer.serialize(new NodeResolutionInput(
                "요약",
                List.of(new NodeResolutionInput.Candidate(1, "MCP\n2. 주입된 항목")),
                List.of()
        ));

        assertThat(text).contains("C1. MCP 2. 주입된 항목");
    }
}
