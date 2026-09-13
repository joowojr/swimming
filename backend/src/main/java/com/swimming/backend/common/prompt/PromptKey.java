package com.swimming.backend.common.prompt;

/**
 * 프롬프트의 논리 이름. 설정의 {@code app.prompt.locations} 키와 1:1로 대응한다.
 *
 * <p>파일 경로가 아니라 이름을 쓰는 이유는, 어떤 프롬프트를 쓸지(코드)와 그것이 어디
 * 있는지(설정)를 분리하기 위해서다. 버전을 바꾸거나 파일로 빼는 것은 설정만 건드린다.
 */
public enum PromptKey {

    /** 메모를 여러 폴더 중 하나로 분류한다. */
    TASK_ORGANIZER("task-organizer"),

    /** 폴더가 이미 정해진 상태에서 메모에서 할 일만 뽑는다. */
    TASK_EXTRACTOR("task-extractor"),

    /** 저장한 문서 하나를 소화해 Summary·Topic·Subject를 뽑는다. */
    SOURCE_DIGEST("source-digest"),

    /** 후보별 매칭 결과를 사용해 Subject를 기존 Subject에 맞추거나 새 이름으로 정한다. */
    NODE_RESOLUTION_V2("node-resolution-v2");

    private final String configName;

    PromptKey(String configName) {
        this.configName = configName;
    }

    public String configName() {
        return configName;
    }
}
