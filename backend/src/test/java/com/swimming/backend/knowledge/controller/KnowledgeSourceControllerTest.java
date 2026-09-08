package com.swimming.backend.knowledge.controller;

import com.swimming.backend.common.dto.CursorPage;
import com.swimming.backend.common.exception.GlobalExceptionHandler;
import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.knowledge.domain.SourceProcessingStatus;
import com.swimming.backend.knowledge.domain.SourceSearchOperator;
import com.swimming.backend.knowledge.dto.in.NodeRef;
import com.swimming.backend.knowledge.dto.in.SourceDeleteResponse;
import com.swimming.backend.knowledge.dto.in.SourceDetailResponse;
import com.swimming.backend.knowledge.dto.in.SourceResponse;
import com.swimming.backend.knowledge.usecase.SourceCollectUseCase;
import com.swimming.backend.knowledge.usecase.SourceDeleteUseCase;
import com.swimming.backend.knowledge.usecase.SourceQueryUseCase;
import com.swimming.backend.knowledge.usecase.SourceReadUseCase;
import com.swimming.backend.knowledge.usecase.SourceSearchUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KnowledgeSourceControllerTest {

    private static final UUID SOURCE_ID = UUID.fromString("0d0b1f22-0000-0000-0000-000000000001");
    private static final UUID SUBJECT_ID = UUID.fromString("3c220000-0000-0000-0000-000000000002");
    private static final UUID TOPIC_ID = UUID.fromString("9f1a0000-0000-0000-0000-000000000003");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-09-01T09:00:00Z");
    private static final Instant READ_AT = Instant.parse("2026-09-02T10:00:00Z");

    private SourceSearchUseCase searchUseCase;
    private SourceQueryUseCase queryUseCase;
    private SourceCollectUseCase collectUseCase;
    private SourceDeleteUseCase deleteUseCase;
    private SourceReadUseCase readUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        searchUseCase = mock(SourceSearchUseCase.class);
        queryUseCase = mock(SourceQueryUseCase.class);
        collectUseCase = mock(SourceCollectUseCase.class);
        deleteUseCase = mock(SourceDeleteUseCase.class);
        readUseCase = mock(SourceReadUseCase.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new KnowledgeSourceController(
                        searchUseCase, queryUseCase, collectUseCase, deleteUseCase, readUseCase
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthUserArgumentResolver(
                        new AuthUser(1L, "user@example.com")
                ))
                .build();
    }

    @Test
    @DisplayName("관계 검색 조건과 페이지 조건을 UseCase에 전달한다")
    void searchesWithRelations() throws Exception {
        when(searchUseCase.search(
                1L, List.of(SUBJECT_ID), TOPIC_ID, SourceSearchOperator.OR,
                10L, 5, "cursor-abc"
        )).thenReturn(new CursorPage<>(List.of(), null, false));

        mockMvc.perform(get("/api/knowledge/sources")
                        .param("subjectIds", SUBJECT_ID.toString())
                        .param("topicId", TOPIC_ID.toString())
                        .param("operator", "OR")
                        .param("folderId", "10")
                        .param("size", "5")
                        .param("cursor", "cursor-abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false));

        verify(searchUseCase).search(
                1L, List.of(SUBJECT_ID), TOPIC_ID, SourceSearchOperator.OR,
                10L, 5, "cursor-abc"
        );
    }

    @Test
    @DisplayName("잘못된 operator와 페이지 크기는 400으로 거절한다")
    void rejectsInvalidParameters() throws Exception {
        mockMvc.perform(get("/api/knowledge/sources").param("operator", "XOR"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/knowledge/sources").param("size", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Source 상세는 원문 정보까지 담아 200으로 돌려준다")
    void returnsSourceDetail() throws Exception {
        when(queryUseCase.get(1L, SOURCE_ID)).thenReturn(new SourceDetailResponse(
                SOURCE_ID,
                "Spring AI MCP Reference",
                "https://docs.spring.io/mcp.html",
                "https://docs.spring.io/mcp.html",
                "docs.spring.io",
                "article",
                "Spring",
                PUBLISHED_AT,
                10L,
                CREATED_AT,
                READ_AT,
                SourceProcessingStatus.COMPLETED,
                null,
                false,
                "MCP Server를 구성하는 방법을 설명한다.",
                new NodeRef(TOPIC_ID, "MCP 서버 구현하기"),
                List.of(new NodeRef(SUBJECT_ID, "MCP"))
        ));

        mockMvc.perform(get("/api/knowledge/sources/" + SOURCE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceId").value(SOURCE_ID.toString()))
                .andExpect(jsonPath("$.canonicalUrl").value("https://docs.spring.io/mcp.html"))
                .andExpect(jsonPath("$.author").value("Spring"))
                .andExpect(jsonPath("$.folderId").value(10))
                .andExpect(jsonPath("$.publishedAt").value("2026-03-01T00:00:00Z"))
                .andExpect(jsonPath("$.createdAt").value("2026-09-01T09:00:00Z"))
                .andExpect(jsonPath("$.readAt").value("2026-09-02T10:00:00Z"))
                .andExpect(jsonPath("$.topic.nodeId").value(TOPIC_ID.toString()))
                .andExpect(jsonPath("$.subjects[0].title").value("MCP"))
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    @Test
    @DisplayName("링크를 지우면 폴더의 활성 링크 여부를 200으로 돌려준다")
    void deletesSource() throws Exception {
        when(deleteUseCase.delete(1L, SOURCE_ID))
                .thenReturn(new SourceDeleteResponse(10L, false));

        mockMvc.perform(delete("/api/knowledge/sources/" + SOURCE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folderId").value(10))
                .andExpect(jsonPath("$.hasSource").value(false));

        verify(deleteUseCase).delete(1L, SOURCE_ID);
    }

    @Test
    @DisplayName("링크를 읽음으로 표시하면 본문 없이 204를 돌려준다")
    void marksSourceRead() throws Exception {
        mockMvc.perform(post("/api/knowledge/sources/" + SOURCE_ID + "/read"))
                .andExpect(status().isNoContent());

        verify(readUseCase).markRead(1L, SOURCE_ID);
    }

    @Test
    @DisplayName("읽음 표시를 지우면 본문 없이 204를 돌려준다")
    void marksSourceUnread() throws Exception {
        mockMvc.perform(delete("/api/knowledge/sources/" + SOURCE_ID + "/read"))
                .andExpect(status().isNoContent());

        verify(readUseCase).markUnread(1L, SOURCE_ID);
    }

    @Test
    @DisplayName("저장된 본문을 다시 분석하면 갱신된 Source 카드를 돌려준다")
    void retriesSource() throws Exception {
        when(collectUseCase.retry(1L, SOURCE_ID)).thenReturn(new SourceResponse(
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
                "다시 분석한 요약",
                new NodeRef(TOPIC_ID, "MCP 서버 구현하기"),
                List.of(new NodeRef(SUBJECT_ID, "MCP"))
        ));

        mockMvc.perform(post("/api/knowledge/sources/" + SOURCE_ID + "/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceId").value(SOURCE_ID.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.summary").value("다시 분석한 요약"));

        verify(collectUseCase).retry(1L, SOURCE_ID);
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
