package com.swimming.backend.common.prompt;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 설정에 적힌 위치에서 프롬프트를 읽어 조각을 끼워 넣은 뒤 들고 있는다.
 *
 * <p>기동 시 전부 조립한다. 경로 오타나 조각 누락이 첫 LLM 호출이 아니라 기동에서
 * 드러나야 하기 때문이다. 대신 프롬프트를 고치면 재기동해야 반영된다.
 *
 * <p>조각 표시는 HTML 주석이다. 마크다운에서 렌더링되지 않고, 프롬프트 본문의 산문·백틱·
 * 중괄호와 충돌할 수 없다. 치환 문법을 본문에 들이면 그 문자들이 전부 지뢰가 된다.
 */
@Repository
public class ResourcePromptRepository implements PromptRepository {

    /** 줄 전체가 마커일 때만 조각으로 본다. 문장 안에 우연히 들어간 문자열은 건드리지 않는다. */
    private static final Pattern FRAGMENT_MARKER =
            Pattern.compile("^\\s*<!--\\s*fragment:\\s*([A-Za-z0-9_-]+)\\s*-->\\s*$");

    private final Map<PromptKey, String> prompts;

    public ResourcePromptRepository(PromptProperties properties, ResourceLoader resourceLoader) {
        Map<String, String> fragments = new HashMap<>();
        properties.fragments().forEach((name, location) ->
                fragments.put(name, read("fragment " + name, location.strip(), resourceLoader).strip()));

        Map<PromptKey, String> loaded = new EnumMap<>(PromptKey.class);

        for (PromptKey key : PromptKey.values()) {
            String location = properties.locations().get(key.configName());

            if (location == null || location.isBlank()) {
                throw new IllegalStateException(
                        "app.prompt.locations." + key.configName() + " 설정이 없습니다"
                );
            }

            String skeleton = read(key.configName(), location.strip(), resourceLoader);
            loaded.put(key, assemble(key, skeleton, fragments));
        }

        this.prompts = Map.copyOf(loaded);
    }

    private String assemble(PromptKey key, String skeleton, Map<String, String> fragments) {
        StringBuilder assembled = new StringBuilder(skeleton.length());

        for (String line : skeleton.split("\n", -1)) {
            Matcher marker = FRAGMENT_MARKER.matcher(line);

            if (marker.matches()) {
                String name = marker.group(1);
                String fragment = fragments.get(name);

                if (fragment == null) {
                    throw new IllegalStateException(
                            "프롬프트 조각이 없습니다: " + key.configName() + " 이(가) '" + name
                                    + "' 을 참조하지만 app.prompt.fragments 에 없습니다"
                    );
                }

                assembled.append(fragment);
            } else {
                assembled.append(line);
            }

            assembled.append('\n');
        }

        return assembled.toString().strip();
    }

    private String read(String name, String location, ResourceLoader resourceLoader) {
        Resource resource = resourceLoader.getResource(location);

        if (!resource.exists()) {
            throw new IllegalStateException(
                    "프롬프트 파일을 찾을 수 없습니다: " + name + " -> " + location
            );
        }

        try {
            String content = resource.getContentAsString(StandardCharsets.UTF_8);

            if (content.isBlank()) {
                throw new IllegalStateException(
                        "프롬프트가 비어 있습니다: " + name + " -> " + location
                );
            }

            return content;

        } catch (IOException e) {
            throw new IllegalStateException(
                    "프롬프트를 읽지 못했습니다: " + name + " -> " + location, e
            );
        }
    }

    @Override
    public String get(PromptKey key) {
        return prompts.get(key);
    }
}
