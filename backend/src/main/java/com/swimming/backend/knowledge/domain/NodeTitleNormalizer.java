package com.swimming.backend.knowledge.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 같은 개념이 표기만 달라 다른 노드로 갈라지는 것을 막는 비교용 키를 만든다.
 *
 * <p>{@code AWS-OIDC}, {@code aws_oidc}, {@code aws oidc}는 모두 {@code awsoidc}가 된다.
 * Subject 후보는 LLM이 문서마다 새로 적어 내므로 대소문자와 구분자가 쉽게 흔들린다.
 *
 * <p>여기서 걷어내는 것은 표기 차이뿐이다. {@code OIDC}와 {@code OpenID Connect}처럼 글자가
 * 다른 같은 개념은 이 방식으로 합칠 수 없다. 별칭 해석은 별도 단계가 맡는다.
 *
 * <p>기호는 남긴다. {@code C#}과 {@code C}, {@code Node.js}와 {@code Node}는 서로 다른
 * 개념인데 기호까지 지우면 하나로 합쳐진다. 잘못 합치면 되돌릴 방법이 없지만, 갈라진 것은
 * 나중에 합칠 수 있다.
 */
public final class NodeTitleNormalizer {

    /** 표기마다 다르게 쓰이는 구분자. 앞뒤 공백도 이 패턴으로 함께 사라진다. */
    private static final Pattern SEPARATORS = Pattern.compile("[\\s_-]+");

    private NodeTitleNormalizer() {
    }

    public static String normalize(String title) {
        if (title == null) {
            return "";
        }

        // 한글 조합형과 전각 문자를 먼저 표준 형태로 모은다. 이 단계를 건너뛰면 눈에 같아
        // 보이는 글자가 다른 코드 포인트로 남아 뒤의 비교가 무의미해진다.
        String composed = Normalizer.normalize(title, Normalizer.Form.NFKC);

        return SEPARATORS.matcher(composed).replaceAll("").toLowerCase(Locale.ROOT);
    }
}
