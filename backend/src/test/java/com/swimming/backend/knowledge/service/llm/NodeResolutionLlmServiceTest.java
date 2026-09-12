package com.swimming.backend.knowledge.service.llm;

import com.swimming.backend.knowledge.dto.out.NodeResolutionInputV2;
import com.swimming.backend.knowledge.dto.out.NodeResolutionResultV2;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class NodeResolutionLlmServiceTest {

    @Test
    @DisplayName("후보와 재사용 Subject에 프롬프트용 C/R 번호를 부여한다")
    void 프롬프트용_참조번호를_부여한다() {
        UUID oidcId = UUID.randomUUID();
        UUID oauthId = UUID.randomUUID();
        UUID jwtId = UUID.randomUUID();
        NodeResolutionLlmRequest.ReusableSubject oidc = subject(oidcId, "OpenID Connect");
        NodeResolutionLlmRequest.ReusableSubject oauth = subject(oauthId, "OAuth 2.0");

        NodeResolutionLlmService.IndexedRequest indexed =
                NodeResolutionLlmService.toIndexedRequest(new NodeResolutionLlmRequest(
                        "인증 표준을 설명한다.",
                        List.of(
                                candidate("oidc", "OIDC", oidc, oauth),
                                candidate("authentication", "authentication", oidc)
                        ),
                        List.of(oidc, subject(jwtId, "JSON Web Token"))
                ));

        assertThat(indexed.input().candidates())
                .extracting(NodeResolutionInputV2.Candidate::index,
                        NodeResolutionInputV2.Candidate::value)
                .containsExactly(tuple(1, "OIDC"), tuple(2, "authentication"));
        assertThat(indexed.input().candidates().getFirst().matches())
                .extracting(NodeResolutionInputV2.Match::index,
                        NodeResolutionInputV2.Match::value)
                .containsExactly(tuple(1, "OpenID Connect"), tuple(2, "OAuth 2.0"));
        assertThat(indexed.input().candidates().get(1).matches())
                .extracting(NodeResolutionInputV2.Match::index)
                .containsExactly(1);
        assertThat(indexed.input().contextSubjects())
                .extracting(NodeResolutionInputV2.ContextSubject::index,
                        NodeResolutionInputV2.ContextSubject::value)
                .containsExactly(tuple(3, "JSON Web Token"));
        assertThat(indexed.subjectIdByReuseIndex())
                .containsEntry(1, oidcId)
                .containsEntry(2, oauthId)
                .containsEntry(3, jwtId);
    }

    @Test
    @DisplayName("LLM 응답의 C/R 번호를 후보 key와 Subject ID로 변환한다")
    void 응답_참조번호를_실제_식별자로_변환한다() {
        UUID oidcId = UUID.randomUUID();
        NodeResolutionLlmService.IndexedRequest indexed =
                NodeResolutionLlmService.toIndexedRequest(new NodeResolutionLlmRequest(
                        "요약",
                        List.of(
                                candidate("oidc", "OIDC", subject(oidcId, "OpenID Connect")),
                                candidate("saml", "saml")
                        ),
                        List.of()
                ));

        List<NodeResolutionLlmDecision> decisions = NodeResolutionLlmService.translate(
                new NodeResolutionResultV2(List.of(
                        new NodeResolutionResultV2.Decision(1, 1, "무시할 값"),
                        new NodeResolutionResultV2.Decision(1, 0, "뒤의 중복 결정"),
                        new NodeResolutionResultV2.Decision(2, 0, "  SAML  ")
                )),
                indexed
        );

        assertThat(decisions).containsExactly(
                NodeResolutionLlmDecision.reuse("oidc", oidcId),
                NodeResolutionLlmDecision.create("saml", "SAML")
        );
    }

    @Test
    @DisplayName("잘못된 후보 번호와 R 번호는 제외하고 사용할 수 있는 결정만 반환한다")
    void 잘못된_참조번호를_제외한다() {
        NodeResolutionLlmService.IndexedRequest indexed =
                NodeResolutionLlmService.toIndexedRequest(new NodeResolutionLlmRequest(
                        "요약",
                        List.of(candidate("oidc", "OIDC"), candidate("saml", "saml")),
                        List.of()
                ));

        List<NodeResolutionLlmDecision> decisions = NodeResolutionLlmService.translate(
                new NodeResolutionResultV2(List.of(
                        new NodeResolutionResultV2.Decision(0, 0, "잘못된 후보"),
                        new NodeResolutionResultV2.Decision(1, -1, "음수 R"),
                        new NodeResolutionResultV2.Decision(1, 99, "없는 R"),
                        new NodeResolutionResultV2.Decision(2, 0, "SAML")
                )),
                indexed
        );

        assertThat(decisions).containsExactly(
                NodeResolutionLlmDecision.create("saml", "SAML"));
    }

    @Test
    @DisplayName("사용할 수 있는 LLM 결정이 하나도 없으면 실패한다")
    void 사용할_수_있는_결정이_없으면_실패한다() {
        NodeResolutionLlmService.IndexedRequest indexed =
                NodeResolutionLlmService.toIndexedRequest(new NodeResolutionLlmRequest(
                        "요약",
                        List.of(candidate("oidc", "OIDC")),
                        List.of()
                ));

        assertThatThrownBy(() -> NodeResolutionLlmService.translate(
                new NodeResolutionResultV2(List.of(
                        new NodeResolutionResultV2.Decision(1, 99, "")
                )),
                indexed
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no usable decision");
    }

    private static NodeResolutionLlmRequest.Candidate candidate(
            String key,
            String value,
            NodeResolutionLlmRequest.ReusableSubject... matches
    ) {
        return new NodeResolutionLlmRequest.Candidate(key, value, List.of(matches));
    }

    private static NodeResolutionLlmRequest.ReusableSubject subject(UUID id, String title) {
        return new NodeResolutionLlmRequest.ReusableSubject(id, title);
    }
}
