package com.swimming.backend.folder.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FolderTagTest {

    @Test
    @DisplayName("프로젝트 태그가 자신의 이름을 정규화한다")
    void normalizesName() {
        FolderTag tag = FolderTag.create(1L, " 취준 ");

        assertThat(tag.getUserId()).isEqualTo(1L);
        assertThat(tag.getName()).isEqualTo("취준");
    }
}
