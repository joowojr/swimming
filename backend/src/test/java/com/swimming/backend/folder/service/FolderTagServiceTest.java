package com.swimming.backend.folder.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.folder.domain.FolderTag;
import com.swimming.backend.folder.repository.FolderRepository;
import com.swimming.backend.folder.repository.FolderTagRepository;
import com.swimming.backend.folder.repository.entity.FolderTagEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FolderTagServiceTest {

    private FolderTagRepository folderTagRepository;
    private FolderRepository folderRepository;
    private FolderTagService folderTagService;

    @BeforeEach
    void setUp() {
        folderTagRepository = mock(FolderTagRepository.class);
        folderRepository = mock(FolderRepository.class);
        folderTagService = new FolderTagService(folderTagRepository, folderRepository);
        when(folderTagRepository.saveAndFlush(any(FolderTagEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(folderTagRepository.deleteOwnedTag(any(), any())).thenReturn(1);
    }

    @Test
    @DisplayName("태그 이름의 앞뒤 공백을 제거해 순수 도메인으로 반환한다")
    void createsTrimmedProjectTag() {
        when(folderTagRepository.saveAndFlush(any(FolderTagEntity.class)))
                .thenAnswer(invocation -> {
                    FolderTagEntity entity = invocation.getArgument(0);
                    ReflectionTestUtils.setField(entity, "id", 3L);
                    return entity;
                });

        FolderTag tag = folderTagService.create(FolderTag.create(1L, " 취준 "));

        assertThat(tag.getId()).isEqualTo(3L);
        assertThat(tag.getUserId()).isEqualTo(1L);
        assertThat(tag.getName()).isEqualTo("취준");
    }

    @Test
    @DisplayName("같은 사용자는 같은 이름의 태그를 중복 생성할 수 없다")
    void rejectsDuplicateProjectTagName() {
        when(folderTagRepository.existsByUserIdAndName(1L, "취준")).thenReturn(true);

        assertThatThrownBy(() -> folderTagService.create(FolderTag.create(1L, "취준")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROJECT_TAG_ALREADY_EXISTS));
        verify(folderTagRepository, never()).saveAndFlush(any(FolderTagEntity.class));
    }

    @Test
    @DisplayName("동시 생성으로 태그 이름이 중복되어도 충돌 예외로 변환한다")
    void convertsDuplicateConstraintViolationToBusinessException() {
        when(folderTagRepository.saveAndFlush(any(FolderTagEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> folderTagService.create(FolderTag.create(1L, "취준")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROJECT_TAG_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("사용자의 태그 선택지를 이름순 순수 도메인 목록으로 조회한다")
    void returnsUsersProjectTags() {
        when(folderTagRepository.findAllByUserIdOrderByNameAsc(1L))
                .thenReturn(List.of(tagEntity(1L, "사이드 프로젝트"), tagEntity(2L, "취준")));

        List<FolderTag> tags = folderTagService.getAll(1L);

        assertThat(tags).extracting(FolderTag::getName)
                .containsExactly("사이드 프로젝트", "취준");
    }

    @Test
    @DisplayName("사용자가 소유한 태그 이름을 변경한다")
    void updatesOwnedProjectTagName() {
        FolderTagEntity entity = tagEntity(3L, "취준");
        when(folderTagRepository.findByIdAndUserId(3L, 1L)).thenReturn(java.util.Optional.of(entity));

        FolderTag updated = folderTagService.updateName(1L, 3L, " 이직 ");

        assertThat(updated.getName()).isEqualTo("이직");
        verify(folderTagRepository).findByIdAndUserId(3L, 1L);
        verify(folderTagRepository).flush();
    }

    @Test
    @DisplayName("다른 태그와 같은 이름으로 변경할 수 없다")
    void rejectsDuplicateProjectTagNameOnUpdateName() {
        FolderTagEntity entity = tagEntity(3L, "취준");
        when(folderTagRepository.findByIdAndUserId(3L, 1L)).thenReturn(java.util.Optional.of(entity));
        when(folderTagRepository.existsByUserIdAndNameAndIdNot(1L, "이직", 3L))
                .thenReturn(true);

        assertThatThrownBy(() -> folderTagService.updateName(1L, 3L, "이직"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PROJECT_TAG_ALREADY_EXISTS));
        verify(folderTagRepository, never()).flush();
    }

    @Test
    @DisplayName("태그를 연결 프로젝트에서 해제한 뒤 삭제한다")
    void clearsTagFromProjectsBeforeDeletingIt() {
        folderTagService.delete(1L, 3L);

        var inOrder = org.mockito.Mockito.inOrder(folderRepository, folderTagRepository);
        inOrder.verify(folderRepository).clearTagFromOwnedProjects(1L, 3L);
        inOrder.verify(folderTagRepository).deleteOwnedTag(3L, 1L);
        verify(folderTagRepository, never()).findByIdAndUserId(3L, 1L);
    }

    @Test
    @DisplayName("다른 사용자의 태그는 수정하거나 삭제할 수 없다")
    void rejectsUnownedProjectTagChanges() {
        when(folderTagRepository.findByIdAndUserId(3L, 2L)).thenReturn(java.util.Optional.empty());
        when(folderTagRepository.deleteOwnedTag(3L, 2L)).thenReturn(0);

        assertThatThrownBy(() -> folderTagService.updateName(2L, 3L, "취준"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROJECT_TAG_NOT_FOUND));
        assertThatThrownBy(() -> folderTagService.delete(2L, 3L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROJECT_TAG_NOT_FOUND));
        verify(folderRepository).clearTagFromOwnedProjects(2L, 3L);
    }

    private FolderTagEntity tagEntity(Long id, String name) {
        FolderTagEntity entity = FolderTagEntity.from(FolderTag.create(1L, name));
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
