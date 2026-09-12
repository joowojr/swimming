package com.swimming.backend.knowledge.prompt;

import com.swimming.backend.knowledge.dto.out.NodeResolutionInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NodeResolutionInputSerializerTest {

    @Test
    @DisplayName("Summary와 미해결 후보, 재사용 가능한 Subject를 XML로 직렬화한다")
    void serializesResolutionContext() {
        NodeResolutionInput input = new NodeResolutionInput(
                "OIDC와 OAuth <차이>를 설명한다.",
                List.of(
                        new NodeResolutionInput.Candidate(1, "OIDC"),
                        new NodeResolutionInput.Candidate(2, "OAuth & Security")
                ),
                List.of(new NodeResolutionInput.ExistingSubject(1, "OpenID Connect"))
        );

        String xml = NodeResolutionInputSerializer.serialize(input);

        assertThat(xml)
                .contains("OIDC와 OAuth &lt;차이&gt;를 설명한다.")
                .contains("<candidate index=\"1\">OIDC</candidate>")
                .contains("<candidate index=\"2\">OAuth &amp; Security</candidate>")
                .contains("<subject index=\"1\">OpenID Connect</subject>");
    }

    @Test
    @DisplayName("유사 Source에서 Subject를 찾지 못하면 기존 후보 요소를 생략한다")
    void omitsEmptyExistingSubjects() {
        String xml = NodeResolutionInputSerializer.serialize(new NodeResolutionInput(
                "요약", List.of(new NodeResolutionInput.Candidate(1, "MCP")), List.of()
        ));

        assertThat(xml).doesNotContain("<existing-subjects>");
    }
}
