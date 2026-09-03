package com.swimming.backend.common.prompt;

/**
 * 프롬프트의 논리 이름. 설정의 {@code app.prompt.locations} 키와 1:1로 대응한다.
 *
 * <p>파일 경로가 아니라 이름을 쓰는 이유는, 어떤 프롬프트를 쓸지(코드)와 그것이 어디
 * 있는지(설정)를 분리하기 위해서다. 버전을 바꾸거나 파일로 빼는 것은 설정만 건드린다.
 */
public enum PromptKey {

    TASK_ORGANIZER("task-organizer");

    private final String configName;

    PromptKey(String configName) {
        this.configName = configName;
    }

    public String configName() {
        return configName;
    }
}
