package com.swimming.backend.common.config.google;

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
 * 설정 record에 항목을 추가하고 {@code google.yml}에 넣지 않으면 기동에서야 드러난다.
 * 이름이 어긋나는 경우도 마찬가지다. 여기서 미리 잡는다.
 */
class GooglePropertiesBindingTest {

    @Test
    @DisplayName("auth 설정 항목이 google.yml 에 모두 있다")
    void authPropertiesArePresent() throws Exception {
        Map<String, Object> yaml = at("app", "google", "auth");

        assertThat(yaml.keySet())
                .containsExactlyInAnyOrderElementsOf(kebabComponentsOf(GoogleProperties.Auth.class));
    }

    @Test
    @DisplayName("youtube 설정 항목이 google.yml 에 모두 있다")
    void youtubePropertiesArePresent() throws Exception {
        Map<String, Object> yaml = at("app", "google", "youtube");

        assertThat(yaml.keySet())
                .containsExactlyInAnyOrderElementsOf(kebabComponentsOf(GoogleProperties.Youtube.class));
    }

    @Test
    @DisplayName("모든 프로필이 google.yml 을 끌어다 쓴다")
    void everyProfileImportsGoogleConfig() throws Exception {
        for (String profile : List.of("application-local.yml", "application-prod.yml")) {
            assertThat(imports(profile))
                    .as("%s 가 google.yml 을 import 하지 않습니다", profile)
                    .contains("classpath:google.yml");
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> imports(String resource) throws Exception {
        Map<String, Object> config = (Map<String, Object>) load(resource).get("spring");
        return (List<String>) ((Map<String, Object>) config.get("config")).get("import");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> at(String... path) throws Exception {
        Object node = load("google.yml");

        for (String key : path) {
            assertThat(node).as("google.yml 에 %s 가 없습니다", key).isInstanceOf(Map.class);
            node = ((Map<String, Object>) node).get(key);
        }
        return (Map<String, Object>) node;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> load(String resource) throws Exception {
        try (InputStream yaml = new ClassPathResource(resource).getInputStream()) {
            return (Map<String, Object>) new Yaml().load(yaml);
        }
    }

    private List<String> kebabComponentsOf(Class<?> record) {
        return Arrays.stream(record.getRecordComponents())
                .map(RecordComponent::getName)
                .map(name -> name.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT))
                .toList();
    }
}
