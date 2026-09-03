package com.swimming.backend.common.prompt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

/**
 * 프롬프트 위치 설정.
 *
 * <p>값은 Spring 리소스 위치라 접두사로 저장소가 정해진다.
 * <ul>
 *   <li>{@code classpath:prompts/...} — jar 안에 번들된 기본값</li>
 *   <li>{@code file:/opt/swimming/prompts/...} — 이미지를 다시 만들지 않고 내용을 고칠 때</li>
 * </ul>
 *
 * @param locations 프롬프트 이름 → 골격 파일
 * @param fragments 조각 이름 → 파일. 골격의 {@code <!-- fragment: 이름 -->} 줄에 끼워 넣는다
 */
@Validated
@ConfigurationProperties("app.prompt")
public record PromptProperties(
        @NotEmpty Map<String, @NotBlank String> locations,
        Map<String, @NotBlank String> fragments
) {
    public PromptProperties {
        fragments = fragments == null ? Map.of() : Map.copyOf(fragments);
    }
}
