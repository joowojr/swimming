package com.swimming.backend.folder.controller;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.common.exception.GlobalExceptionHandler;
import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.folder.domain.FolderStatus;
import com.swimming.backend.folder.domain.FolderStatusFilter;
import com.swimming.backend.folder.dto.CreateFolderRequest;
import com.swimming.backend.folder.dto.FolderDetailResponse;
import com.swimming.backend.folder.dto.FolderResponse;
import com.swimming.backend.folder.dto.FolderTagResponse;
import com.swimming.backend.folder.dto.PinFolderRequest;
import com.swimming.backend.folder.dto.UpdateFolderRequest;
import com.swimming.backend.folder.dto.UpdateFolderStatusRequest;
import com.swimming.backend.folder.dto.UpdateFolderTagRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
                FolderStatus.NOT_STARTED,
                new FolderTagResponse(3L, "취준"),
                false,
                null,
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
                .andExpect(jsonPath("$.status").value("NOT_STARTED"))
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
                FolderStatus.NOT_STARTED,
                new FolderTagResponse(4L, "포트폴리오"),
                false,
                null,
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
        when(folderUseCase.getAll(1L, FolderStatusFilter.ACTIVE)).thenReturn(List.of(response(
                10L,
                "프로젝트",
                "설명",
                null,
                FolderStatus.IN_PROGRESS
        )));

        mockMvc.perform(get("/api/folders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].name").value("프로젝트"))
                .andExpect(jsonPath("$[0].hasSource").value(false));
    }

    @Test
    @DisplayName("status 파라미터로 고른 상태의 폴더 목록을 반환한다")
    void returnsFoldersFilteredByStatusParameter() throws Exception {
        when(folderUseCase.getAll(1L, FolderStatusFilter.ARCHIVED)).thenReturn(List.of(response(
                11L,
                "보관한 프로젝트",
                "설명",
                null,
                FolderStatus.ARCHIVED
        )));

        mockMvc.perform(get("/api/folders").param("status", "ARCHIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(11))
                .andExpect(jsonPath("$[0].status").value("ARCHIVED"));
    }

    @Test
    @DisplayName("status 파라미터가 알 수 없는 값이면 400을 반환한다")
    void rejectsUnknownStatusParameter() throws Exception {
        mockMvc.perform(get("/api/folders").param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest());
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
                Instant.parse("2026-09-11T10:00:00Z"),
                7L
        ));

        mockMvc.perform(get("/api/folders/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("프로젝트"))
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.sourceCount").value(7))
                .andExpect(jsonPath("$.pinnedAt").value("2026-09-11T10:00:00Z"))
                .andExpect(jsonPath("$.tasks").doesNotExist())
                .andExpect(jsonPath("$.progress").doesNotExist());
    }

    @Test
    @DisplayName("프로젝트 상태를 보관됨으로 수정한다")
    void updatesFolderStatus() throws Exception {
        UpdateFolderStatusRequest request = new UpdateFolderStatusRequest(FolderStatus.ARCHIVED);
        when(folderUseCase.updateStatus(1L, 10L, request)).thenReturn(response(
                10L,
                "프로젝트",
                "설명",
                null,
                FolderStatus.ARCHIVED
        ));

        mockMvc.perform(patch("/api/folders/10/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status":"ARCHIVED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
        verify(folderUseCase).updateStatus(1L, 10L, request);
    }

    @Test
    @DisplayName("프로젝트 상태를 빠뜨리면 400으로 반환한다")
    void rejectsFolderStatusRequestWithoutStatus() throws Exception {
        mockMvc.perform(patch("/api/folders/10/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").exists());
    }

    @Test
    @DisplayName("폴더를 고정하면 고정한 시각을 담아 200으로 반환한다")
    void pinsFolder() throws Exception {
        when(folderUseCase.pin(1L, 10L, new PinFolderRequest(true)))
                .thenReturn(new FolderResponse(
                        10L,
                        "프로젝트",
                        "설명",
                        null,
                        FolderStatus.IN_PROGRESS,
                        null,
                        false,
                        Instant.parse("2026-09-11T10:00:00Z"),
                        Instant.parse("2026-08-19T10:00:00Z"),
                        Instant.parse("2026-09-11T10:00:00Z")
                ));

        mockMvc.perform(patch("/api/folders/10/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pinned":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.pinnedAt").value("2026-09-11T10:00:00Z"));
        verify(folderUseCase).pin(1L, 10L, new PinFolderRequest(true));
    }

    @Test
    @DisplayName("고정 여부를 빠뜨리면 400으로 반환한다")
    void rejectsPinRequestWithoutPinnedField() throws Exception {
        mockMvc.perform(patch("/api/folders/10/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {}
                                """))
                .andExpect(status().isBadRequest());
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

    @Test
    @DisplayName("폴더의 태그를 바꾸고 바뀐 폴더를 반환한다")
    void updatesFolderTag() throws Exception {
        UpdateFolderTagRequest request = new UpdateFolderTagRequest(null, "리서치");
        when(folderUseCase.updateTag(1L, 10L, request)).thenReturn(new FolderResponse(
                10L,
                "프로젝트",
                "설명",
                null,
                FolderStatus.IN_PROGRESS,
                new FolderTagResponse(4L, "리서치"),
                false,
                null,
                Instant.parse("2026-08-19T10:00:00Z"),
                Instant.parse("2026-08-19T10:00:00Z")
        ));

        mockMvc.perform(put("/api/folders/10/tag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newTagName":"리서치"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tag.id").value(4))
                .andExpect(jsonPath("$.tag.name").value("리서치"));
    }

    @Test
    @DisplayName("폴더 태그 변경에서 새 태그 이름이 공백이면 필드 오류를 반환한다")
    void rejectsBlankNewTagNameOnTagUpdate() throws Exception {
        mockMvc.perform(put("/api/folders/10/tag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newTagName":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newTagName").exists());
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
                false,
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
