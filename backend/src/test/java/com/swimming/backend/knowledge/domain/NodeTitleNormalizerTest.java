package com.swimming.backend.knowledge.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class NodeTitleNormalizerTest {

    @ParameterizedTest
    @ValueSource(strings = {"AWS-OIDC", "aws_oidc", "aws oidc", "  AWS   OIDC  ", "Aws-Oidc"})
    @DisplayName("대소문자와 구분자만 다른 표기는 같은 키가 된다")
    void 표기가_달라도_같은_키가_된다(String title) {
        assertThat(NodeTitleNormalizer.normalize(title)).isEqualTo("awsoidc");
    }

    @Test
    @DisplayName("글자가 다른 같은 개념은 합쳐지지 않는다. 별칭 해석은 이 단계의 몫이 아니다")
    void 글자가_다르면_다른_키다() {
        assertThat(NodeTitleNormalizer.normalize("OIDC"))
                .isNotEqualTo(NodeTitleNormalizer.normalize("OpenID Connect"));
    }

    @Test
    @DisplayName("기호는 남긴다. C#과 C가 한 노드로 합쳐지면 안 된다")
    void 기호는_지우지_않는다() {
        assertThat(NodeTitleNormalizer.normalize("C#")).isEqualTo("c#");
        assertThat(NodeTitleNormalizer.normalize("Node.js")).isEqualTo("node.js");
    }

    @Test
    @DisplayName("전각 문자와 조합형 한글은 표준 형태로 모은 뒤 비교한다")
    void 유니코드_표기_차이를_흡수한다() {
        assertThat(NodeTitleNormalizer.normalize("ＯＩＤＣ")).isEqualTo("oidc");
        assertThat(NodeTitleNormalizer.normalize("인증"))
                .isEqualTo(NodeTitleNormalizer.normalize("\u110b\u1175\u11ab\u110c\u1173\u11bc"));
    }

    @Test
    @DisplayName("이름이 없거나 구분자뿐이면 빈 키다. 호출하는 쪽이 걸러낸다")
    void 비어_있는_이름은_빈_키다() {
        assertThat(NodeTitleNormalizer.normalize(null)).isEmpty();
        assertThat(NodeTitleNormalizer.normalize("   ")).isEmpty();
        assertThat(NodeTitleNormalizer.normalize("-_-")).isEmpty();
    }
}
