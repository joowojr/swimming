package com.swimming.backend.common.prompt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourcePromptRepositoryTest {

    private static final Map<String, String> FRAGMENTS = Map.of(
            "splitting", "classpath:prompts/task-organizer/_splitting.md",
            "titles", "classpath:prompts/task-organizer/_titles.md"
    );

    private static ResourcePromptRepository repository(Map<String, String> locations,
                                                       Map<String, String> fragments) {
        return new ResourcePromptRepository(
                new PromptProperties(locations, fragments),
                new DefaultResourceLoader()
        );
    }

    private static ResourcePromptRepository defaultRepository() {
        return repository(
                Map.of(
                        PromptKey.TASK_ORGANIZER.configName(), "classpath:prompts/task-organizer/classify.md",
                        PromptKey.TASK_EXTRACTOR.configName(), "classpath:prompts/task-organizer/extract.md",
                        PromptKey.SOURCE_DIGEST.configName(), "classpath:prompts/knowledge/digest.md",
                        PromptKey.NODE_RESOLUTION_V2.configName(), "classpath:prompts/knowledge/node-resolution-v2.md"
                ),
                FRAGMENTS
        );
    }

    private static String classpathText(String path) throws Exception {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("조립된 분류 프롬프트가 분해 전 원문과 같다")
    void 분류_프롬프트는_원문과_동일하다() throws Exception {
        String assembled = defaultRepository().get(PromptKey.TASK_ORGANIZER);
        String original = classpathText("prompts/task-organizer-v4.md").strip();

        assertThat(assembled).isEqualTo(original);
    }

    @Test
    @DisplayName("두 프롬프트가 공통 조각을 글자 그대로 공유한다")
    void 공통_조각은_두_프롬프트에_모두_들어간다() throws Exception {
        ResourcePromptRepository repository = defaultRepository();
        String splitting = classpathText("prompts/task-organizer/_splitting.md").strip();
        String titles = classpathText("prompts/task-organizer/_titles.md").strip();

        assertThat(repository.get(PromptKey.TASK_ORGANIZER)).contains(splitting).contains(titles);
        assertThat(repository.get(PromptKey.TASK_EXTRACTOR)).contains(splitting).contains(titles);
    }

    @Test
    @DisplayName("추출 프롬프트에는 폴더 분류 규칙이 들어가지 않는다")
    void 추출_프롬프트는_분류_규칙을_담지_않는다() {
        String extract = defaultRepository().get(PromptKey.TASK_EXTRACTOR);

        assertThat(extract)
                .doesNotContain("# Folder classification")
                .doesNotContain("whose Folder is unknown")
                .doesNotContain("Being the only Folder");
    }

    @Test
    @DisplayName("조립 후 해석되지 않은 마커가 남지 않는다")
    void 마커가_남지_않는다() {
        ResourcePromptRepository repository = defaultRepository();

        assertThat(repository.get(PromptKey.TASK_ORGANIZER)).doesNotContain("<!-- fragment:");
        assertThat(repository.get(PromptKey.TASK_EXTRACTOR)).doesNotContain("<!-- fragment:");
    }

    @Test
    @DisplayName("골격이 참조하는 조각이 없으면 기동에서 실패한다")
    void 조각_누락은_기동에서_실패한다() {
        assertThatThrownBy(() -> repository(
                Map.of(
                        PromptKey.TASK_ORGANIZER.configName(), "classpath:prompts/task-organizer/classify.md",
                        PromptKey.TASK_EXTRACTOR.configName(), "classpath:prompts/task-organizer/extract.md",
                        PromptKey.SOURCE_DIGEST.configName(), "classpath:prompts/knowledge/digest.md",
                        PromptKey.NODE_RESOLUTION_V2.configName(), "classpath:prompts/knowledge/node-resolution-v2.md"
                ),
                Map.of("titles", FRAGMENTS.get("titles"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("splitting");
    }

    @Test
    @DisplayName("file 접두사로 이미지 밖의 파일을 읽는다")
    void file로_읽는다() throws Exception {
        Path file = Files.createTempFile("prompt", ".md");
        Files.writeString(file, "파일에서 읽은 프롬프트");

        String prompt = repository(
                Map.of(
                        PromptKey.TASK_ORGANIZER.configName(), "file:" + file,
                        PromptKey.TASK_EXTRACTOR.configName(), "file:" + file,
                        PromptKey.SOURCE_DIGEST.configName(), "file:" + file,
                        PromptKey.NODE_RESOLUTION_V2.configName(), "file:" + file
                ),
                Map.of()).get(PromptKey.TASK_ORGANIZER);

        assertThat(prompt).isEqualTo("파일에서 읽은 프롬프트");
    }

    @Test
    @DisplayName("경로가 틀리면 첫 LLM 호출이 아니라 기동에서 실패한다")
    void 없는_경로는_기동에서_실패한다() {
        assertThatThrownBy(() -> repository(
                Map.of(
                        PromptKey.TASK_ORGANIZER.configName(), "classpath:prompts/does-not-exist.md",
                        PromptKey.TASK_EXTRACTOR.configName(), "classpath:prompts/task-organizer/extract.md",
                        PromptKey.SOURCE_DIGEST.configName(), "classpath:prompts/knowledge/digest.md",
                        PromptKey.NODE_RESOLUTION_V2.configName(), "classpath:prompts/knowledge/node-resolution-v2.md"
                ),
                FRAGMENTS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("찾을 수 없습니다");
    }

    @Test
    @DisplayName("프롬프트 위치 설정이 없으면 기동에서 실패한다")
    void 설정_누락은_기동에서_실패한다() {
        assertThatThrownBy(() -> repository(
                Map.of(
                        PromptKey.TASK_ORGANIZER.configName(), "classpath:prompts/task-organizer/classify.md",
                        PromptKey.SOURCE_DIGEST.configName(), "classpath:prompts/knowledge/digest.md",
                        PromptKey.NODE_RESOLUTION_V2.configName(), "classpath:prompts/knowledge/node-resolution-v2.md"
                ),
                FRAGMENTS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("task-extractor");
    }
}
