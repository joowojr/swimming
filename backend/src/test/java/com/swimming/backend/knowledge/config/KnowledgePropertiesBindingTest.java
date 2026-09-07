package com.swimming.backend.knowledge.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 설정 record에 항목을 추가하고 {@code application.yml}에 넣지 않으면 기동에서야 드러난다.
 * 이름이 어긋나는 경우도 마찬가지다. 여기서 미리 잡는다.
 */
class KnowledgePropertiesBindingTest {

    @SuppressWarnings("unchecked")
    private Map<String, Object> at(String... path) throws Exception {
        try (InputStream yaml = new ClassPathResource("application.yml").getInputStream()) {
            Object node = new Yaml().load(yaml);

            for (String key : path) {
                assertThat(node).as("application.yml 에 %s 가 없습니다", key).isInstanceOf(Map.class);
                node = ((Map<String, Object>) node).get(key);
            }
            return (Map<String, Object>) node;
        }
    }

    private List<String> kebabComponentsOf(Class<?> record) {
        return Arrays.stream(record.getRecordComponents())
                .map(RecordComponent::getName)
                .map(name -> name.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT))
                .toList();
    }

    @Test
    @DisplayName("digest 설정 항목이 application.yml 에 모두 있다")
    void digestPropertiesArePresent() throws Exception {
        Map<String, Object> yaml = at("app", "knowledge", "digest");

        assertThat(yaml.keySet())
                .containsExactlyInAnyOrderElementsOf(kebabComponentsOf(KnowledgeDigestProperties.class));
    }

    @Test
    @DisplayName("fetch 설정 항목이 application.yml 에 모두 있다")
    void fetchPropertiesArePresent() throws Exception {
        Map<String, Object> yaml = at("app", "knowledge", "fetch");

        assertThat(yaml.keySet())
                .containsExactlyInAnyOrderElementsOf(kebabComponentsOf(KnowledgeFetchProperties.class));
    }

    @Test
    @DisplayName("fetch.render 설정 항목이 application.yml 에 모두 있다")
    void renderPropertiesArePresent() throws Exception {
        Map<String, Object> yaml = at("app", "knowledge", "fetch", "render");

        assertThat(yaml.keySet())
                .containsExactlyInAnyOrderElementsOf(kebabComponentsOf(KnowledgeFetchProperties.Render.class));
    }
}
