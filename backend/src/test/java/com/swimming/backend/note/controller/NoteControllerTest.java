package com.swimming.backend.note.controller;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.common.exception.GlobalExceptionHandler;
import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.note.domain.NoteContextType;
import com.swimming.backend.note.domain.NoteStatus;
import com.swimming.backend.note.dto.in.NoteCreateRequest;
import com.swimming.backend.note.dto.in.NoteCreateResponse;
import com.swimming.backend.note.dto.in.NoteResponse;
import com.swimming.backend.note.dto.in.NoteUpdateRequest;
import com.swimming.backend.note.usecase.NoteUseCase;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NoteControllerTest {

    private static final int NOTE_CONTENT_MAX_LENGTH = 1024;

    private NoteUseCase noteUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        noteUseCase = mock(NoteUseCase.class);
        NoteController controller = new NoteController(noteUseCase);
        AuthUser authUser = new AuthUser(1L, "user@example.com");
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthUserArgumentResolver(authUser))
                .build();
    }

    @Test
    @DisplayName("Note 생성 시 Location 헤더와 생성된 ID만 반환한다")
    void createsNoteWithIdOnly() throws Exception {
        NoteCreateRequest request = new NoteCreateRequest(
                "떠오른 일",
                NoteContextType.DEFAULT,
                null,
                null
        );
        when(noteUseCase.create(1L, request)).thenReturn(new NoteCreateResponse(7L));

        mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content":"떠오른 일",
                                  "contextType":"DEFAULT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/notes/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    @Test
    @DisplayName("Note 내용이 비어 있으면 필드 오류를 반환한다")
    void rejectsBlankNoteContent() throws Exception {
        mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content":" ",
                                  "contextType":"DEFAULT"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.content").exists());
    }

    @Test
    @DisplayName("Note 생성 시 내용은 1024자까지 허용한다")
    void acceptsMaxLengthNoteContentOnCreate() throws Exception {
        String content = "가".repeat(NOTE_CONTENT_MAX_LENGTH);
        NoteCreateRequest request = new NoteCreateRequest(
                content,
                NoteContextType.DEFAULT,
                null,
                null
        );
        when(noteUseCase.create(1L, request)).thenReturn(new NoteCreateResponse(7L));

        mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content":"%s",
                                  "contextType":"DEFAULT"
                                }
                                """.formatted(content)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Note 생성 시 내용이 1024자를 초과하면 필드 오류를 반환한다")
    void rejectsTooLongNoteContentOnCreate() throws Exception {
        String content = "가".repeat(NOTE_CONTENT_MAX_LENGTH + 1);

        mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content":"%s",
                                  "contextType":"DEFAULT"
                                }
                                """.formatted(content)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.content")
                        .value("노트 내용은 1024자 이하여야 합니다"));
    }

    @Test
    @DisplayName("프로젝트 컨텍스트의 활성 Note 목록을 반환한다")
    void returnsProjectNotes() throws Exception {
        when(noteUseCase.getAll(1L, NoteStatus.ACTIVE, null, 10L, null))
                .thenReturn(List.of(response(1L, "프로젝트 메모", NoteContextType.PROJECT, 10L, null)));

        mockMvc.perform(get("/api/notes").param("folderId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].content").value("프로젝트 메모"))
                .andExpect(jsonPath("$[0].contextType").value("PROJECT"));
    }

    @Test
    @DisplayName("보관 상태를 지정하면 보관된 Note 목록을 반환한다")
    void returnsArchivedNotes() throws Exception {
        when(noteUseCase.getAll(1L, NoteStatus.ARCHIVED, null, null, null))
                .thenReturn(List.of(response(
                        1L,
                        "보관 메모",
                        NoteStatus.ARCHIVED,
                        NoteContextType.DEFAULT,
                        null,
                        null
                )));

        mockMvc.perform(get("/api/notes").param("status", "ARCHIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ARCHIVED"));
    }

    @Test
    @DisplayName("Note 상세를 반환한다")
    void returnsNoteDetail() throws Exception {
        when(noteUseCase.getOne(1L, 1L))
                .thenReturn(response(1L, "메모", NoteContextType.DEFAULT, null, null));

        mockMvc.perform(get("/api/notes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Note 내용을 수정한다")
    void updatesNoteContent() throws Exception {
        NoteUpdateRequest request = new NoteUpdateRequest("수정한 메모");
        when(noteUseCase.update(1L, 1L, request))
                .thenReturn(response(1L, "수정한 메모", NoteContextType.DEFAULT, null, null));

        mockMvc.perform(patch("/api/notes/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"수정한 메모"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("수정한 메모"));
    }

    @Test
    @DisplayName("Note 수정 시 내용은 1024자까지 허용한다")
    void acceptsMaxLengthNoteContentOnUpdate() throws Exception {
        String content = "가".repeat(NOTE_CONTENT_MAX_LENGTH);
        NoteUpdateRequest request = new NoteUpdateRequest(content);
        when(noteUseCase.update(1L, 1L, request))
                .thenReturn(response(1L, content, NoteContextType.DEFAULT, null, null));

        mockMvc.perform(patch("/api/notes/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"%s"}
                                """.formatted(content)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Note 수정 시 내용이 1024자를 초과하면 필드 오류를 반환한다")
    void rejectsTooLongNoteContentOnUpdate() throws Exception {
        String content = "가".repeat(NOTE_CONTENT_MAX_LENGTH + 1);

        mockMvc.perform(patch("/api/notes/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"%s"}
                                """.formatted(content)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.content")
                        .value("노트 내용은 1024자 이하여야 합니다"));
    }

    @Test
    @DisplayName("Note를 보관하면 본문 없이 성공한다")
    void archivesNote() throws Exception {
        mockMvc.perform(patch("/api/notes/1/archive"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(noteUseCase).archive(1L, 1L);
    }

    @Test
    @DisplayName("보관된 Note를 복원하면 본문 없이 성공한다")
    void restoresNote() throws Exception {
        mockMvc.perform(patch("/api/notes/1/restore"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(noteUseCase).restore(1L, 1L);
    }

    @Test
    @DisplayName("Note를 삭제하면 본문 없이 성공한다")
    void deletesNote() throws Exception {
        mockMvc.perform(delete("/api/notes/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(noteUseCase).delete(1L, 1L);
    }

    @Test
    @DisplayName("다른 사용자의 Note는 찾을 수 없음으로 반환한다")
    void hidesAnotherUsersNote() throws Exception {
        when(noteUseCase.getOne(1L, 1L))
                .thenThrow(new BusinessException(ErrorCode.NOTE_NOT_FOUND));

        mockMvc.perform(get("/api/notes/1"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOTE_NOT_FOUND"));
    }

    private NoteResponse response(
            Long id,
            String content,
            NoteStatus status,
            NoteContextType contextType,
            Long folderId,
            Long sessionId
    ) {
        return new NoteResponse(
                id,
                content,
                status,
                contextType,
                folderId,
                sessionId,
                LocalDateTime.of(2026, 8, 24, 10, 0),
                LocalDateTime.of(2026, 8, 24, 10, 0)
        );
    }

    private NoteResponse response(
            Long id,
            String content,
            NoteContextType contextType,
            Long folderId,
            Long sessionId
    ) {
        return response(
                id,
                content,
                NoteStatus.ACTIVE,
                contextType,
                folderId,
                sessionId
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
