package com.swimming.backend.folder.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.folder.domain.Folder;
import com.swimming.backend.folder.domain.FolderStatus;
import com.swimming.backend.folder.domain.FolderTag;
import com.swimming.backend.folder.dto.CreateFolderRequest;
import com.swimming.backend.folder.dto.FolderDetailResponse;
import com.swimming.backend.folder.dto.FolderResponse;
import com.swimming.backend.folder.dto.UpdateFolderRequest;
import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.folder.service.FolderTagService;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.in.TaskSummaryResponse;
import com.swimming.backend.task.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FolderUseCaseTest {

    @Mock
    private FolderService folderService;

    @Mock
    private FolderTagService folderTagService;

    @Mock
    private TaskService taskService;

    private FolderUseCase folderUseCase;

    @BeforeEach
    void setUp() {
        folderUseCase = new FolderUseCase(
                folderService,
                folderTagService,
                taskService
        );
    }

    @Test
    @DisplayName("프로젝트를 생성하고 응답 DTO로 변환한다")
    void createsProjectAndMapsResponse() {
        LocalDate targetDate = LocalDate.of(2026, 9, 30);
        FolderTag tag = FolderTag.restore(3L, 1L, "취준", null, null);
        CreateFolderRequest request = new CreateFolderRequest(
                "프로젝트",
                "설명",
                targetDate,
                3L,
                null
        );
        Folder folder = Folder.restore(
                10L, 1L, tag, "프로젝트", "설명", targetDate,
                FolderStatus.IN_PROGRESS, false, null, null
        );
        when(folderTagService.getOne(1L, 3L)).thenReturn(tag);
        when(folderService.create(any(Folder.class))).thenReturn(folder);

        FolderResponse response = folderUseCase.create(1L, request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("프로젝트");
        assertThat(response.status()).isEqualTo(FolderStatus.IN_PROGRESS);
        assertThat(response.tag().id()).isEqualTo(3L);
        assertThat(response.tag().name()).isEqualTo("취준");
        verify(folderTagService).getOne(1L, 3L);
        verify(folderService).create(argThat(created ->
                created.getUserId().equals(1L)
                        && created.getTag().getId().equals(3L)
                        && created.getTargetDate().equals(targetDate)
        ));
    }

    @Test
    @DisplayName("새 태그를 생성한 뒤 같은 트랜잭션에서 프로젝트에 연결한다")
    void createsNewTagAndConnectsItToProject() {
        FolderTag tag = FolderTag.restore(4L, 1L, "포트폴리오", null, null);
        Folder folder = Folder.restore(
                10L, 1L, tag, "프로젝트", "설명", null,
                FolderStatus.IN_PROGRESS, false, null, null
        );
        CreateFolderRequest request = new CreateFolderRequest(
                "프로젝트",
                "설명",
                null,
                null,
                " 포트폴리오 "
        );
        when(folderTagService.create(any(FolderTag.class))).thenReturn(tag);
        when(folderService.create(any(Folder.class))).thenReturn(folder);

        FolderResponse response = folderUseCase.create(1L, request);

        assertThat(response.tag().id()).isEqualTo(4L);
        assertThat(response.tag().name()).isEqualTo("포트폴리오");
        verify(folderTagService).create(argThat(created ->
                created.getUserId().equals(1L) && created.getName().equals("포트폴리오")
        ));
        verify(folderService).create(argThat(created -> created.getTag().getId().equals(4L)));
    }

    @Test
    @DisplayName("기존 태그와 새 태그 이름을 동시에 지정할 수 없다")
    void rejectsExistingAndNewTagTogether() {
        CreateFolderRequest request = new CreateFolderRequest(
                "프로젝트",
                "설명",
                null,
                3L,
                "포트폴리오"
        );

        assertThatThrownBy(() -> folderUseCase.create(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(ErrorCode.PROJECT_TAG_SELECTION_CONFLICT));
        verify(folderTagService, never()).create(any(FolderTag.class));
        verify(folderService, never()).create(any(Folder.class));
    }

    @Test
    @DisplayName("프로젝트 목록을 응답 DTO 목록으로 변환한다")
    void returnsProjectListAsResponses() {
        when(folderService.getAll(1L)).thenReturn(List.of(
                project(10L, "첫 번째", "설명 1", null),
                project(11L, "두 번째", "설명 2", null)
        ));

        List<FolderResponse> responses = folderUseCase.getAll(1L);

        assertThat(responses).extracting(FolderResponse::name)
                .containsExactly("첫 번째", "두 번째");
    }

    @Test
    @DisplayName("프로젝트 상세에 Task 목록과 완료 Task 비율을 포함한다")
    void returnsProjectDetailAsResponse() {
        when(folderService.getOne(1L, 10L))
                .thenReturn(project(10L, "프로젝트", "설명", null));
        when(taskService.getSummaries(10L)).thenReturn(List.of(
                new TaskSummaryResponse(1L, "첫째", TaskStatus.DONE, 0),
                new TaskSummaryResponse(2L, "둘째", TaskStatus.DOING, 1)
        ));

        FolderDetailResponse response = folderUseCase.getOne(1L, 10L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.description()).isEqualTo("설명");
        assertThat(response.progress().totalTaskCount()).isEqualTo(2);
        assertThat(response.progress().completedTaskCount()).isEqualTo(1);
        assertThat(response.progress().completionPct()).isEqualTo(50);
        assertThat(response.tasks()).extracting(TaskSummaryResponse::title)
                .containsExactly("첫째", "둘째");
    }

    @Test
    @DisplayName("Task가 없는 프로젝트의 완료 비율은 0이다")
    void returnsZeroProgressWhenProjectHasNoTasks() {
        when(folderService.getOne(1L, 10L))
                .thenReturn(project(10L, "프로젝트", "설명", null));
        when(taskService.getSummaries(10L)).thenReturn(List.of());

        FolderDetailResponse response = folderUseCase.getOne(1L, 10L);

        assertThat(response.progress().totalTaskCount()).isZero();
        assertThat(response.progress().completedTaskCount()).isZero();
        assertThat(response.progress().completionPct()).isZero();
        assertThat(response.tasks()).isEmpty();
    }

    @Test
    @DisplayName("프로젝트를 수정하고 응답 DTO로 변환한다")
    void updatesProjectAndMapsResponse() {
        UpdateFolderRequest request = new UpdateFolderRequest(
                "수정 프로젝트",
                "수정 설명",
                null,
                FolderStatus.ARCHIVED,
                null
        );
        Folder folder = project(10L, "수정 프로젝트", "수정 설명", null);
        folder.update("수정 프로젝트", "수정 설명", null, FolderStatus.ARCHIVED, null);
        when(folderService.update(
                1L, 10L, null, "수정 프로젝트", "수정 설명", null,
                FolderStatus.ARCHIVED
        )).thenReturn(folder);

        FolderResponse response = folderUseCase.update(1L, 10L, request);

        assertThat(response.status()).isEqualTo(FolderStatus.ARCHIVED);
        verify(folderService).update(
                1L, 10L, null, "수정 프로젝트", "수정 설명", null,
                FolderStatus.ARCHIVED
        );
    }

    @Test
    @DisplayName("프로젝트 삭제는 서비스에 soft delete를 위임한다")
    void deletesProject() {
        folderUseCase.delete(1L, 10L);

        verify(folderService).delete(1L, 10L);
    }

    private Folder project(
            Long id,
            String name,
            String description,
            LocalDate targetDate
    ) {
        return Folder.restore(
                id, 1L, null, name, description, targetDate,
                FolderStatus.IN_PROGRESS, false, null, null
        );
    }
}
