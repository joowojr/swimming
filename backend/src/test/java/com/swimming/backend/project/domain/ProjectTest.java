package com.swimming.backend.project.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectTest {

    @Test
    @DisplayName("프로젝트를 생성하면 문자열을 정규화하고 진행 중 상태가 된다")
    void createsInProgressProject() {
        Project project = Project.create(
                1L, null, " 새 프로젝트 ", " 프로젝트 설명 ",
                LocalDate.of(2026, 9, 30)
        );

        assertThat(project.getName()).isEqualTo("새 프로젝트");
        assertThat(project.getDescription()).isEqualTo("프로젝트 설명");
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("프로젝트가 자신의 기본 정보와 상태를 수정한다")
    void updatesProjectState() {
        Project project = Project.create(1L, null, "프로젝트", "설명", null);
        ProjectTag tag = ProjectTag.restore(3L, 1L, "취준", null, null);

        project.update(
                " 수정 프로젝트 ", " 수정 설명 ", LocalDate.of(2026, 10, 1),
                ProjectStatus.ARCHIVED, tag
        );

        assertThat(project.getName()).isEqualTo("수정 프로젝트");
        assertThat(project.getDescription()).isEqualTo("수정 설명");
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);
        assertThat(project.getTag()).isSameAs(tag);
    }
}
