package com.swimming.backend.common.prompt;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/**
 * 설정에 적힌 위치에서 프롬프트를 읽어 들고 있는다.
 *
 * <p>기동 시 전부 읽는다. 경로 오타나 누락된 파일이 첫 LLM 호출이 아니라 기동에서
 * 드러나야 하기 때문이다. 대신 프롬프트를 고치면 재기동해야 반영된다.
 */
@Repository
public class ResourcePromptRepository implements PromptRepository {

    private final Map<PromptKey, String> prompts;

    public ResourcePromptRepository(PromptProperties properties, ResourceLoader resourceLoader) {
        Map<PromptKey, String> loaded = new EnumMap<>(PromptKey.class);

        for (PromptKey key : PromptKey.values()) {
            String location = properties.locations().get(key.configName());

            if (location == null || location.isBlank()) {
                throw new IllegalStateException(
                        "app.prompt.locations." + key.configName() + " 설정이 없습니다"
                );
            }

            loaded.put(key, read(key, location.strip(), resourceLoader));
        }

        this.prompts = Map.copyOf(loaded);
    }

    private String read(PromptKey key, String location, ResourceLoader resourceLoader) {
        Resource resource = resourceLoader.getResource(location);

        if (!resource.exists()) {
            throw new IllegalStateException(
                    "프롬프트 파일을 찾을 수 없습니다: " + key.configName() + " -> " + location
            );
        }

        try {
            String content = resource.getContentAsString(StandardCharsets.UTF_8);

            if (content.isBlank()) {
                throw new IllegalStateException(
                        "프롬프트가 비어 있습니다: " + key.configName() + " -> " + location
                );
            }

            return content;

        } catch (IOException e) {
            throw new IllegalStateException(
                    "프롬프트를 읽지 못했습니다: " + key.configName() + " -> " + location, e
            );
        }
    }

    @Override
    public String get(PromptKey key) {
        return prompts.get(key);
    }
}
