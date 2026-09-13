package com.swimming.backend.folder.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.folder.domain.Folder;
import com.swimming.backend.folder.domain.FolderTag;
import com.swimming.backend.folder.domain.FolderStatus;
import com.swimming.backend.folder.repository.FolderRepository;
import com.swimming.backend.folder.repository.FolderTagRepository;
import com.swimming.backend.folder.repository.entity.FolderEntity;
import com.swimming.backend.folder.repository.entity.FolderTagEntity;
import com.swimming.backend.user.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FolderServiceTest {

    private FolderRepository folderRepository;
    private FolderTagRepository folderTagRepository;
    private EntityManager entityManager;
    private FolderService folderService;

    @BeforeEach
    void setUp() {
        folderRepository = mock(FolderRepository.class);
        folderTagRepository = mock(FolderTagRepository.class);
        entityManager = mock(EntityManager.class);
        folderService = new FolderService(
                folderRepository,
                folderTagRepository,
                entityManager
        );
        when(entityManager.getReference(eq(User.class), anyLong()))
                .thenAnswer(invocation -> user(invocation.getArgument(1)));
        when(folderRepository.saveAndFlush(any(FolderEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("인증된 사용자의 프로젝트를 생성해 순수 도메인으로 반환한다")
    void createsFolderForAuthenticatedUser() {
        when(folderRepository.saveAndFlush(any(FolderEntity.class))).thenAnswer(invocation -> {
            FolderEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 10L);
            return entity;
        });

        Folder folder = folderService.create(Folder.create(
                1L,
                null,
                " 새 프로젝트 ",
                " 프로젝트 설명 ",
                LocalDate.of(2026, 9, 30)
        ));

        assertThat(folder.getId()).isEqualTo(10L);
        assertThat(folder.getName()).isEqualTo("새 프로젝트");
        assertThat(folder.getDescription()).isEqualTo("프로젝트 설명");
        assertThat(folder.getTargetDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(folder.getStatus()).isEqualTo(FolderStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("사용자가 소유한 태그를 프로젝트에 하나 연결한다")
    void createsFolderWithOwnedTag() {
        FolderTagEntity tag = tagEntity(3L, 1L, "취준");
        when(folderTagRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(tag));

        Folder folder = folderService.create(Folder.create(
                1L, tag.toDomain(), "프로젝트", "설명", null
        ));

        assertThat(folder.getTag().getId()).isEqualTo(3L);
        assertThat(folder.getTag().getName()).isEqualTo("취준");
    }

    @Test
    @DisplayName("다른 사용자의 태그를 프로젝트에 연결할 수 없다")
    void rejectsAnotherUsersTag() {
        when(folderTagRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.empty());

        Folder folder = Folder.create(
                1L,
                FolderTag.restore(3L, 1L, "취준", null, null),
                "프로젝트",
                "설명",
                null
        );

        assertThatThrownBy(() -> folderService.create(folder))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FOLDER_TAG_NOT_FOUND));
        verify(folderRepository, never()).saveAndFlush(any(FolderEntity.class));
    }

    @Test
    @DisplayName("사용자의 보관되지 않은 프로젝트만 조회한다")
    void returnsOnlyNonArchivedFoldersForUser() {
        FolderEntity newest = folderEntity(1L, "두 번째 프로젝트", null, null);
        FolderEntity oldest = folderEntity(1L, "첫 번째 프로젝트", LocalDate.of(2026, 10, 1), null);
        when(folderRepository.findAllActiveOrderByPinnedAtDescCreatedAtDesc(
                1L, FolderStatus.ARCHIVED
        )).thenReturn(List.of(newest, oldest));

        List<Folder> folders = folderService.getAll(1L);

        assertThat(folders).extracting(Folder::getName)
                .containsExactly("두 번째 프로젝트", "첫 번째 프로젝트");
    }

    @Test
    @DisplayName("사용자가 소유한 프로젝트 상세를 순수 도메인으로 조회한다")
    void returnsOwnedFolderDetail() {
        FolderEntity entity = folderEntity(1L, "프로젝트", null, null);
        when(folderRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(entity));

        Folder result = folderService.getOne(1L, 10L);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getName()).isEqualTo("프로젝트");
    }

    @Test
    @DisplayName("프로젝트를 수정하면서 목표일을 제거할 수 있다")
    void updatesFolderAndCanRemoveTargetDate() {
        FolderEntity entity = folderEntity(1L, "기존 프로젝트", LocalDate.of(2026, 8, 31), null);
        when(folderRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(entity));

        Folder result = folderService.update(
                1L, 10L, null, " 수정 프로젝트 ", " 수정 설명 ", null,
                FolderStatus.IN_PROGRESS
        );

        assertThat(result.getName()).isEqualTo("수정 프로젝트");
        assertThat(result.getDescription()).isEqualTo("수정 설명");
        assertThat(result.getTargetDate()).isNull();
        assertThat(result.getStatus()).isEqualTo(FolderStatus.IN_PROGRESS);
        verify(folderRepository).findByIdAndUser_IdAndDeletedFalse(10L, 1L);
        verify(folderRepository).flush();
    }

    @Test
    @DisplayName("프로젝트에서 선택한 태그를 해제할 수 있다")
    void removesTagFromFolder() {
        FolderTagEntity tag = tagEntity(3L, 1L, "취준");
        FolderEntity entity = folderEntity(1L, "프로젝트", null, tag);
        when(folderRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(entity));

        Folder result = folderService.update(
                1L, 10L, null, "프로젝트", "설명", null,
                FolderStatus.IN_PROGRESS
        );

        assertThat(result.getTag()).isNull();
    }

    @Test
    @DisplayName("프로젝트를 삭제하지 않고 보관 상태로 변경한다")
    void archivesFolderWithoutDeletingIt() {
        FolderEntity entity = folderEntity(1L, "프로젝트", null, null);
        when(folderRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(entity));

        Folder result = folderService.update(
                1L, 10L, null, "프로젝트", "설명", null,
                FolderStatus.ARCHIVED
        );

        assertThat(result.getStatus()).isEqualTo(FolderStatus.ARCHIVED);
        verify(folderRepository, never()).delete(any(FolderEntity.class));
    }

    @Test
    @DisplayName("폴더를 고정하면 고정한 시각을 기록한다")
    void recordsPinnedAtWhenFolderIsPinned() {
        FolderEntity entity = folderEntity(1L, "프로젝트", null, null);
        when(folderRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(entity));

        Folder result = folderService.pin(1L, 10L, true);

        assertThat(result.getPinnedAt()).isNotNull();
        verify(folderRepository).flush();
        verify(folderRepository, never()).save(any(FolderEntity.class));
    }

    @Test
    @DisplayName("폴더 고정을 해제하면 고정한 시각을 지운다")
    void clearsPinnedAtWhenFolderIsUnpinned() {
        FolderEntity entity = folderEntity(1L, "프로젝트", null, null);
        entity.updatePinnedAt(Instant.parse("2026-09-01T00:00:00Z"));
        when(folderRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(entity));

        Folder result = folderService.pin(1L, 10L, false);

        assertThat(result.getPinnedAt()).isNull();
    }

    @Test
    @DisplayName("고정은 폴더 수정과 별개라 폴더를 수정해도 고정이 풀리지 않는다")
    void keepsPinnedAtWhenFolderIsUpdated() {
        FolderEntity entity = folderEntity(1L, "기존 프로젝트", null, null);
        entity.updatePinnedAt(Instant.parse("2026-09-01T00:00:00Z"));
        when(folderRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(entity));

        Folder result = folderService.update(
                1L, 10L, null, "수정 프로젝트", "수정 설명", null,
                FolderStatus.IN_PROGRESS
        );

        assertThat(result.getPinnedAt()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
    }

    @Test
    @DisplayName("다른 사용자의 폴더는 고정할 수 없다")
    void rejectsPinningAnotherUsersFolder() {
        when(folderRepository.findByIdAndUser_IdAndDeletedFalse(10L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> folderService.pin(2L, 10L, true))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FOLDER_NOT_FOUND));
    }

    @Test
    @DisplayName("다른 사용자의 프로젝트 존재 여부를 노출하지 않는다")
    void hidesWhetherAnotherUsersFolderExists() {
        when(folderRepository.findByIdAndUser_IdAndDeletedFalse(10L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> folderService.getOne(2L, 10L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FOLDER_NOT_FOUND));
    }

    @Test
    @DisplayName("프로젝트를 soft delete하고 연결된 데이터를 보존한다")
    void softDeletesOwnedFolder() {
        FolderEntity entity = folderEntity(1L, "프로젝트", null, null);
        when(folderRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(entity));

        folderService.delete(1L, 10L);

        assertThat(entity.isDeleted()).isTrue();
        verify(folderRepository).findByIdAndUser_IdAndDeletedFalse(10L, 1L);
        verify(folderRepository).flush();
        verify(folderRepository, never()).delete(entity);
    }

    @Test
    @DisplayName("링크가 남은 프로젝트는 삭제하지 않는다")
    void rejectsDeleteWhileSourcesRemain() {
        // 링크는 folder_id가 NOT NULL이라 폴더에서 떼어 둘 자리가 없다.
        FolderEntity entity = folderEntity(1L, "프로젝트", null, null);
        entity.updateHasSource(true);
        when(folderRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> folderService.delete(1L, 10L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FOLDER_HAS_SOURCES));

        assertThat(entity.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("링크 유무는 프로젝트 수정이 덮지 않는다")
    void keepsHasSourceThroughUpdate() {
        // 사용자가 편집하는 속성이 아니다. 수정 경로가 지나가며 끄면 삭제 가드가 뚫린다.
        FolderEntity entity = folderEntity(1L, "프로젝트", null, null);
        entity.updateHasSource(true);
        when(folderRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(entity));

        Folder updated = folderService.update(
                1L, 10L, null, "새 이름", "새 설명", null, FolderStatus.IN_PROGRESS
        );

        assertThat(entity.hasSource()).isTrue();
        assertThat(updated.isHasSource())
                .as("응답에 실릴 도메인도 그대로 들고 나온다")
                .isTrue();
    }

    @Test
    @DisplayName("링크 유무를 기록한다")
    void updatesHasSource() {
        FolderEntity entity = folderEntity(1L, "프로젝트", null, null);
        when(folderRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(entity));

        folderService.updateHasSource(1L, 10L, true);
        assertThat(entity.hasSource()).isTrue();

        folderService.updateHasSource(1L, 10L, false);
        assertThat(entity.hasSource()).isFalse();
    }

    private FolderEntity folderEntity(
            Long userId,
            String name,
            LocalDate targetDate,
            FolderTagEntity tag
    ) {
        Folder folder = Folder.create(
                userId,
                tag == null ? null : tag.toDomain(),
                name,
                "설명",
                targetDate
        );
        FolderEntity entity = FolderEntity.from(folder, user(userId), tag);
        ReflectionTestUtils.setField(entity, "id", 10L);
        return entity;
    }

    private User user(Long userId) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        return user;
    }

    private FolderTagEntity tagEntity(Long id, Long userId, String name) {
        FolderTagEntity entity = FolderTagEntity.from(
                FolderTag.create(userId, name)
        );
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
