package com.swimming.backend.folder.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FolderTest {

    @Test
    void 링크_개수를_증감하고_링크_유무를_계산한다() {
        Folder folder = Folder.create(1L, null, "폴더", "설명", null);
        assertThat(folder.getSourceCount()).isZero();
        assertThat(folder.isHasSource()).isFalse();

        folder.incrementSourceCount();
        folder.incrementSourceCount();
        folder.decrementSourceCount();
        assertThat(folder.getSourceCount()).isEqualTo(1);
        assertThat(folder.isHasSource()).isTrue();

        folder.decrementSourceCount();
        assertThat(folder.getSourceCount()).isZero();
        assertThat(folder.isHasSource()).isFalse();
    }

    @Test
    void 링크가_없으면_개수를_감소시키지_않는다() {
        Folder folder = Folder.create(1L, null, "폴더", "설명", null);
        assertThatThrownBy(folder::decrementSourceCount).isInstanceOf(IllegalStateException.class);
        assertThat(folder.getSourceCount()).isZero();
    }

    @Test
    @DisplayName("프로젝트를 생성하면 문자열을 정규화하고 시작 전 상태가 된다")
    void createsNotStartedFolder() {
        Folder folder = Folder.create(
                1L, null, " 새 프로젝트 ", " 프로젝트 설명 ",
                LocalDate.of(2026, 9, 30)
        );

        assertThat(folder.getName()).isEqualTo("새 프로젝트");
        assertThat(folder.getDescription()).isEqualTo("프로젝트 설명");
        assertThat(folder.getStatus()).isEqualTo(FolderStatus.NOT_STARTED);
    }

    @Test
    @DisplayName("프로젝트가 자신의 기본 정보를 수정한다")
    void updatesFolderState() {
        FolderTag tag = FolderTag.restore(3L, 1L, "취준", null, null);
        Folder folder = Folder.create(1L, tag, "프로젝트", "설명", null);

        folder.update(" 수정 프로젝트 ", " 수정 설명 ", LocalDate.of(2026, 10, 1));

        assertThat(folder.getName()).isEqualTo("수정 프로젝트");
        assertThat(folder.getDescription()).isEqualTo("수정 설명");
        assertThat(folder.getTag())
                .as("태그는 폴더 태그 API만 바꾼다")
                .isSameAs(tag);
    }

    @Test
    @DisplayName("프로젝트가 자신의 상태를 수정한다")
    void updatesFolderStatus() {
        Folder folder = Folder.create(1L, null, "프로젝트", "설명", null);

        folder.updateStatus(FolderStatus.ARCHIVED);

        assertThat(folder.getStatus()).isEqualTo(FolderStatus.ARCHIVED);
    }
}
