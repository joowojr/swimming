package com.swimming.backend.common.prompt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

/**
 * 프롬프트 이름 → 리소스 위치.
 *
 * <p>값은 Spring 리소스 위치라 접두사로 저장소가 정해진다.
 * <ul>
 *   <li>{@code classpath:prompts/task-organizer-v4.md} — jar 안에 번들된 기본값</li>
 *   <li>{@code file:/opt/swimming/prompts/task-organizer.md} — 이미지를 다시 만들지 않고
 *       내용을 고치고 싶을 때</li>
 * </ul>
 */
@Validated
@ConfigurationProperties("app.prompt")
public record PromptProperties(
        @NotEmpty Map<String, @NotBlank String> locations
) {
}
