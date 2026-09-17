package com.swimming.backend.knowledge.controller;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.common.exception.GlobalExceptionHandler;
import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.knowledge.domain.SourceProcessingStatus;
import com.swimming.backend.knowledge.dto.in.NodeRef;
import com.swimming.backend.knowledge.dto.in.SourceResponse;
import com.swimming.backend.knowledge.dto.in.SourceCollectRequest;
import com.swimming.backend.knowledge.dto.in.SourceCollectResponse;
import com.swimming.backend.common.dto.CursorPage;
import com.swimming.backend.knowledge.usecase.SourceCollectUseCase;
import com.swimming.backend.knowledge.usecase.SourceQueryUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FolderKnowledgeSourceControllerTest {

    private static final UUID SOURCE_ID = UUID.fromString("0d0b1f22-0000-0000-0000-000000000001");
    private static final UUID SUBJECT_ID = UUID.fromString("3c220000-0000-0000-0000-000000000002");
    private static final UUID TOPIC_ID = UUID.fromString("9f1a0000-0000-0000-0000-000000000003");
    private static final Instant CREATED_AT = Instant.parse("2026-09-01T09:00:00Z");

    private SourceCollectUseCase commandUseCase;
    private SourceQueryUseCase queryUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        commandUseCase = mock(SourceCollectUseCase.class);
        queryUseCase = mock(SourceQueryUseCase.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new FolderKnowledgeSourceController(commandUseCase, queryUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new AuthUserArgumentResolver(new AuthUser(1L, "user@example.com"))
                )
                .build();
    }

    private SourceResponse card() {
        return new SourceResponse(
                SOURCE_ID,
                "Spring AI MCP Reference",
                "https://docs.spring.io/mcp.html",
                "docs.spring.io",
                "article",
                CREATED_AT,
                null,
                SourceProcessingStatus.COMPLETED,
                null,
                false,
                "MCP Server를 구성하는 방법을 설명한다.",
                null,
                new NodeRef(TOPIC_ID, "MCP 서버 구현하기"),
                List.of(new NodeRef(SUBJECT_ID, "MCP")),
                new NodeRef(UUID.fromString("00000000-0000-0000-0000-000000000018"), "MCP 서버 개발")
        );
    }

    private SourceResponse contentOnlyCard() {
        return new SourceResponse(
                SOURCE_ID,
                "짧은 메모",
                "https://example.com/short",
                "example.com",
                "article",
                CREATED_AT,
                null,
                SourceProcessingStatus.SOURCE_NOT_DIGEST,
                null,
                false,
                null,
                "짧은 본문",
                null,
                List.of(),
                null
        );
    }

    @Test
    @DisplayName("링크를 저장하면 소화까지 끝난 카드를 200으로 돌려준다")
    void collectsLinks() throws Exception {
        when(commandUseCase.collect(eq(1L), eq(10L), any(SourceCollectRequest.class)))
                .thenReturn(new SourceCollectResponse(List.of(
                        SourceCollectResponse.Item.created("https://docs.spring.io/mcp.html", card())
                )));

        mockMvc.perform(post("/api/folders/10/knowledge/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "urls": ["https://docs.spring.io/mcp.html"] }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].result").value("CREATED"))
                .andExpect(jsonPath("$.items[0].source.status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].source.domain").value("docs.spring.io"))
                .andExpect(jsonPath("$.items[0].source.topic.title").value("MCP 서버 구현하기"))
                .andExpect(jsonPath("$.items[0].source.subjects[0].title").value("MCP"))
                .andExpect(jsonPath("$.items[0].failureMessage").isEmpty())
                .andExpect(jsonPath("$.items[0].retryable").value(false));

        verify(commandUseCase).collect(
                1L, 10L, new SourceCollectRequest(List.of("https://docs.spring.io/mcp.html"))
        );
    }

    @Test
    @DisplayName("가져오지 못한 링크는 사유만 담고 source는 비운다")
    void reportsFailedLink() throws Exception {
        when(commandUseCase.collect(eq(1L), eq(10L), any(SourceCollectRequest.class)))
                .thenReturn(new SourceCollectResponse(List.of(
                        SourceCollectResponse.Item.failed(
                                "https://gone.com", "SOURCE_HTTP_ERROR", false
                        )
                )));

        mockMvc.perform(post("/api/folders/10/knowledge/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "urls": ["https://gone.com"] }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].result").value("FAILED"))
                .andExpect(jsonPath("$.items[0].failureMessage").value("SOURCE_HTTP_ERROR"))
                .andExpect(jsonPath("$.items[0].retryable").value(false))
                .andExpect(jsonPath("$.items[0].source").isEmpty());
    }

    @Test
    @DisplayName("링크를 하나도 넘기지 않으면 400으로 거부한다")
    void rejectsEmptyUrls() throws Exception {
        mockMvc.perform(post("/api/folders/10/knowledge/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "urls": [] }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("남의 Folder에 저장하면 404를 돌려준다")
    void rejectsOtherUsersFolder() throws Exception {
        when(commandUseCase.collect(eq(1L), eq(99L), any(SourceCollectRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.FOLDER_NOT_FOUND));

        mockMvc.perform(post("/api/folders/99/knowledge/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "urls": ["https://a.com"] }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("목록은 카드와 다음 커서를 함께 돌려준다")
    void listsSources() throws Exception {
        when(queryUseCase.list(1L, 10L, null, 20, null))
                .thenReturn(new CursorPage<>(List.of(card()), "cursor-abc", true));

        mockMvc.perform(get("/api/folders/10/knowledge/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sourceId").value(SOURCE_ID.toString()))
                .andExpect(jsonPath("$.items[0].title").value("Spring AI MCP Reference"))
                .andExpect(jsonPath("$.items[0].category.nodeId").value("00000000-0000-0000-0000-000000000018"))
                .andExpect(jsonPath("$.items[0].category.title").value("MCP 서버 개발"))
                .andExpect(jsonPath("$.nextCursor").value("cursor-abc"));
    }

    @Test
    @DisplayName("소화를 생략한 Source는 상태와 짧은 원문을 목록에 돌려준다")
    void listsContentOfSourceWithoutDigestion() throws Exception {
        when(queryUseCase.list(1L, 10L, null, 20, null))
                .thenReturn(new CursorPage<>(List.of(contentOnlyCard()), null, false));

        mockMvc.perform(get("/api/folders/10/knowledge/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("SOURCE_NOT_DIGEST"))
                .andExpect(jsonPath("$.items[0].content").value("짧은 본문"))
                .andExpect(jsonPath("$.items[0].summary").isEmpty())
                .andExpect(jsonPath("$.items[0].topic").isEmpty())
                .andExpect(jsonPath("$.items[0].subjects").isEmpty())
                .andExpect(jsonPath("$.items[0].category").isEmpty());
    }

    @Test
    @DisplayName("목록 조회는 상태·개수·커서를 그대로 전달한다")
    void passesGetListQuery() throws Exception {
        when(queryUseCase.list(1L, 10L, SourceProcessingStatus.PENDING, 5, "cursor-abc"))
                .thenReturn(new CursorPage<>(List.of(), null, false));

        mockMvc.perform(get("/api/folders/10/knowledge/sources")
                        .param("status", "PENDING")
                        .param("size", "5")
                        .param("cursor", "cursor-abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.nextCursor").isEmpty());

        verify(queryUseCase).list(1L, 10L, SourceProcessingStatus.PENDING, 5, "cursor-abc");
    }

    @Test
    @DisplayName("조건을 주지 않으면 최근 20개를 조회한다")
    void usesDefaultLimit() throws Exception {
        when(queryUseCase.list(eq(1L), eq(10L), isNull(), eq(20), isNull()))
                .thenReturn(new CursorPage<>(List.of(), null, false));

        mockMvc.perform(get("/api/folders/10/knowledge/sources"))
                .andExpect(status().isOk());

        verify(queryUseCase).list(1L, 10L, null, 20, null);
    }

    private record AuthUserArgumentResolver(AuthUser authUser)
            implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType() == AuthUser.class;
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
