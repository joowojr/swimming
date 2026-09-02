package com.swimming.backend.folder.controller;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.common.exception.GlobalExceptionHandler;
import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.folder.domain.FolderStatus;
import com.swimming.backend.folder.dto.CreateFolderRequest;
import com.swimming.backend.folder.dto.FolderDetailResponse;
import com.swimming.backend.folder.dto.FolderProgressResponse;
import com.swimming.backend.folder.dto.FolderResponse;
import com.swimming.backend.folder.dto.FolderTagResponse;
import com.swimming.backend.folder.dto.UpdateFolderRequest;
import com.swimming.backend.folder.usecase.FolderUseCase;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.in.TaskSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FolderControllerTest {

    private FolderUseCase folderUseCase;
    private MockMvc mockMvc;
    private AuthUser authUser;

    @BeforeEach
    void setUp() {
        folderUseCase = mock(FolderUseCase.class);
        authUser = new AuthUser(1L, "user@example.com");
        FolderController folderController = new FolderController(folderUseCase);
        mockMvc = MockMvcBuilders
                .standaloneSetup(folderController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthUserArgumentResolver(authUser))
                .build();
    }

    @Test
    @DisplayName("프로젝트 생성 시 Location 헤더와 생성 결과를 반환한다")
    void createsFolderWithLocationHeader() throws Exception {
        CreateFolderRequest request = new CreateFolderRequest(
                "프로젝트",
                "설명",
                LocalDate.of(2026, 9, 30),
                3L,
                null
        );
        when(folderUseCase.create(1L, request)).thenReturn(new FolderResponse(
                10L,
                "프로젝트",
                "설명",
                LocalDate.of(2026, 9, 30),
                FolderStatus.IN_PROGRESS,
                new FolderTagResponse(3L, "취준"),
                Instant.parse("2026-08-19T10:00:00Z"),
                Instant.parse("2026-08-19T10:00:00Z")
        ));

        mockMvc.perform(post("/api/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"프로젝트",
                                  "description":"설명",
                                  "targetDate":"2026-09-30",
                                  "tagId":3
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/folders/10"))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.tag.id").value(3))
                .andExpect(jsonPath("$.tag.name").value("취준"));
    }

    @Test
    @DisplayName("프로젝트 생성 요청에서 새 태그 이름을 함께 전달할 수 있다")
    void createsFolderWithNewTagName() throws Exception {
        CreateFolderRequest request = new CreateFolderRequest(
                "프로젝트",
                "설명",
                null,
                null,
                "포트폴리오"
        );
        when(folderUseCase.create(1L, request)).thenReturn(new FolderResponse(
                10L,
                "프로젝트",
                "설명",
                null,
                FolderStatus.IN_PROGRESS,
                new FolderTagResponse(4L, "포트폴리오"),
                Instant.parse("2026-08-20T10:00:00Z"),
                Instant.parse("2026-08-20T10:00:00Z")
        ));

        mockMvc.perform(post("/api/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"프로젝트",
                                  "description":"설명",
                                  "targetDate":null,
                                  "tagId":null,
                                  "newTagName":"포트폴리오"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tag.id").value(4))
                .andExpect(jsonPath("$.tag.name").value("포트폴리오"));
    }

    @Test
    @DisplayName("프로젝트 생성 입력이 유효하지 않으면 필드 오류를 반환한다")
    void returnsFieldErrorsForInvalidCreateRequest() throws Exception {
        mockMvc.perform(post("/api/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" ","description":"","targetDate":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.description").exists());
    }

    @Test
    @DisplayName("새 태그 이름이 공백이거나 30자를 초과하면 필드 오류를 반환한다")
    void returnsFieldErrorForInvalidNewTagName() throws Exception {
        mockMvc.perform(post("/api/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"프로젝트",
                                  "description":"설명",
                                  "newTagName":" "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newTagName").exists());

        mockMvc.perform(post("/api/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"프로젝트",
                                  "description":"설명",
                                  "newTagName":"1234567890123456789012345678901"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newTagName").exists());
    }

    @Test
    @DisplayName("인증 사용자의 진행 중 프로젝트 목록을 반환한다")
    void returnsCurrentUsersActiveFolders() throws Exception {
        when(folderUseCase.getAll(1L)).thenReturn(List.of(response(
                10L,
                "프로젝트",
                "설명",
                null,
                FolderStatus.IN_PROGRESS
        )));

        mockMvc.perform(get("/api/folders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].name").value("프로젝트"));
    }

    @Test
    @DisplayName("인증 사용자가 소유한 프로젝트 상세를 반환한다")
    void returnsOwnedFolderDetail() throws Exception {
        when(folderUseCase.getOne(1L, 10L)).thenReturn(new FolderDetailResponse(
                10L,
                "프로젝트",
                "설명",
                null,
                FolderStatus.ARCHIVED,
                null,
                new FolderProgressResponse(2, 1, 50),
                List.of(
                        new TaskSummaryResponse(
                                1L,
                                "완료 Task",
                                TaskStatus.DONE,
                                0
                        ),
                        new TaskSummaryResponse(
                                2L,
                                "진행 Task",
                                TaskStatus.DOING,
                                1
                        )
                )
        ));

        mockMvc.perform(get("/api/folders/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.progress.totalTaskCount").value(2))
                .andExpect(jsonPath("$.progress.completedTaskCount").value(1))
                .andExpect(jsonPath("$.progress.completionPct").value(50))
                .andExpect(jsonPath("$.tasks[0].id").value(1))
                .andExpect(jsonPath("$.tasks[0].status").value("DONE"))
                .andExpect(jsonPath("$.tasks[1].orderIdx").value(1));
    }

    @Test
    @DisplayName("프로젝트 상태를 보관됨으로 수정한다")
    void updatesFolderStatus() throws Exception {
        UpdateFolderRequest request = new UpdateFolderRequest(
                "프로젝트",
                "설명",
                null,
                FolderStatus.ARCHIVED,
                null
        );
        when(folderUseCase.update(1L, 10L, request)).thenReturn(response(
                10L,
                "프로젝트",
                "설명",
                null,
                FolderStatus.ARCHIVED
        ));

        mockMvc.perform(patch("/api/folders/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"프로젝트",
                                  "description":"설명",
                                  "targetDate":null,
                                  "status":"ARCHIVED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    @DisplayName("소유하지 않은 프로젝트는 찾을 수 없음으로 반환한다")
    void returnsNotFoundWithoutRevealingOwnership() throws Exception {
        when(folderUseCase.getOne(1L, 10L))
                .thenThrow(new BusinessException(ErrorCode.FOLDER_NOT_FOUND));

        mockMvc.perform(get("/api/folders/10"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FOLDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("프로젝트 삭제는 본문 없이 성공한다")
    void deletesFolder() throws Exception {
        mockMvc.perform(delete("/api/folders/10"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(folderUseCase).delete(1L, 10L);
    }

    private FolderResponse response(
            Long id,
            String name,
            String description,
            LocalDate targetDate,
            FolderStatus status
    ) {
        return new FolderResponse(
                id,
                name,
                description,
                targetDate,
                status,
                null,
                Instant.parse("2026-08-19T10:00:00Z"),
                Instant.parse("2026-08-19T10:00:00Z")
        );
    }

    private record AuthUserArgumentResolver(AuthUser authUser)
            implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType() == AuthUser.class
                    && parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory
        ) {
            return authUser;
        }
    }
}
