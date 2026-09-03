package com.swimming.backend.common.prompt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourcePromptRepositoryTest {

    private static ResourcePromptRepository repository(String location) {
        return new ResourcePromptRepository(
                new PromptProperties(Map.of(PromptKey.TASK_ORGANIZER.configName(), location)),
                new DefaultResourceLoader()
        );
    }

    @Test
    @DisplayName("classpath 접두사로 jar 안에 번들된 프롬프트를 읽는다")
    void classpath로_읽는다() {
        String prompt = repository("classpath:prompts/task-organizer-v4.md")
                .get(PromptKey.TASK_ORGANIZER);

        assertThat(prompt).isNotBlank();
    }

    @Test
    @DisplayName("file 접두사로 이미지 밖의 파일을 읽는다 - 재빌드 없이 프롬프트를 고칠 수 있다")
    void file로_읽는다() throws Exception {
        Path file = Files.createTempFile("prompt", ".md");
        Files.writeString(file, "파일에서 읽은 프롬프트");

        String prompt = repository("file:" + file).get(PromptKey.TASK_ORGANIZER);

        assertThat(prompt).isEqualTo("파일에서 읽은 프롬프트");
    }

    @Test
    @DisplayName("경로가 틀리면 첫 LLM 호출이 아니라 생성 시점에 실패한다")
    void 없는_경로는_기동에서_실패한다() {
        assertThatThrownBy(() -> repository("classpath:prompts/does-not-exist.md"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("찾을 수 없습니다");
    }

    @Test
    @DisplayName("프롬프트 위치 설정이 없으면 기동에서 실패한다")
    void 설정_누락은_기동에서_실패한다() {
        assertThatThrownBy(() -> new ResourcePromptRepository(
                new PromptProperties(Map.of()), new DefaultResourceLoader()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("설정이 없습니다");
    }

    @Test
    @DisplayName("프롬프트 파일이 비어 있으면 기동에서 실패한다")
    void 빈_파일은_기동에서_실패한다() throws Exception {
        Path file = Files.createTempFile("empty", ".md");

        assertThatThrownBy(() -> repository("file:" + file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("비어 있습니다");
    }
}
