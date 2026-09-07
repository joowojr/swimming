package com.swimming.backend.knowledge.controller;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.common.exception.GlobalExceptionHandler;
import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.SourceProcessingStatus;
import com.swimming.backend.knowledge.dto.in.NodeDetailResponse;
import com.swimming.backend.knowledge.dto.in.NodeRef;
import com.swimming.backend.knowledge.dto.in.SourceDetailResponse;
import com.swimming.backend.knowledge.dto.in.SourceDeleteResponse;
import com.swimming.backend.knowledge.usecase.NodeDeleteUseCase;
import com.swimming.backend.knowledge.usecase.NodeDetailUseCase;
import com.swimming.backend.knowledge.usecase.SourceDeleteUseCase;
import com.swimming.backend.knowledge.usecase.SourceDetailUseCase;
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

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KnowledgeNodeControllerTest {

    private static final UUID SOURCE_ID = UUID.fromString("0d0b1f22-0000-0000-0000-000000000001");
    private static final UUID SUBJECT_ID = UUID.fromString("3c220000-0000-0000-0000-000000000002");
    private static final UUID TOPIC_ID = UUID.fromString("9f1a0000-0000-0000-0000-000000000003");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-09-01T09:00:00Z");

    private SourceDetailUseCase sourceDetailUseCase;
    private NodeDetailUseCase nodeDetailUseCase;
    private SourceDeleteUseCase sourceDeleteUseCase;
    private NodeDeleteUseCase nodeDeleteUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        sourceDetailUseCase = mock(SourceDetailUseCase.class);
        nodeDetailUseCase = mock(NodeDetailUseCase.class);
        sourceDeleteUseCase = mock(SourceDeleteUseCase.class);
        nodeDeleteUseCase = mock(NodeDeleteUseCase.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new KnowledgeNodeController(
                        sourceDetailUseCase, nodeDetailUseCase, sourceDeleteUseCase, nodeDeleteUseCase
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new AuthUserArgumentResolver(new AuthUser(1L, "user@example.com"))
                )
                .build();
    }

    @Test
    @DisplayName("Source 상세는 원문 정보까지 담아 200으로 돌려준다")
    void returnsSourceDetail() throws Exception {
        when(sourceDetailUseCase.get(1L, SOURCE_ID)).thenReturn(new SourceDetailResponse(
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
                SourceProcessingStatus.COMPLETED,
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
                .andExpect(jsonPath("$.topic.nodeId").value(TOPIC_ID.toString()))
                .andExpect(jsonPath("$.subjects[0].title").value("MCP"))
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    @Test
    @DisplayName("Topic 상세는 문서 하나와 걸치는 개념을 돌려준다")
    void returnsNodeDetail() throws Exception {
        when(nodeDetailUseCase.get(1L, TOPIC_ID)).thenReturn(new NodeDetailResponse(
                TOPIC_ID,
                NodeType.TOPIC,
                "MCP 서버 구현하기",
                CREATED_AT,
                List.of(new NodeDetailResponse.SourceRef(
                        SOURCE_ID, "Spring AI MCP Reference", "MCP Server를 구성한다."
                )),
                List.of(),
                List.of(new NodeRef(SUBJECT_ID, "MCP"))
        ));

        mockMvc.perform(get("/api/knowledge/nodes/" + TOPIC_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("TOPIC"))
                .andExpect(jsonPath("$.createdAt").value("2026-09-01T09:00:00Z"))
                .andExpect(jsonPath("$.sources[0].sourceId").value(SOURCE_ID.toString()))
                .andExpect(jsonPath("$.topics").isEmpty())
                .andExpect(jsonPath("$.subjects[0].title").value("MCP"));
    }

    @Test
    @DisplayName("SOURCE 노드를 노드 상세로 부르면 404를 준다")
    void rejectsSourceNode() throws Exception {
        when(nodeDetailUseCase.get(1L, SOURCE_ID))
                .thenThrow(new BusinessException(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND));

        mockMvc.perform(get("/api/knowledge/nodes/" + SOURCE_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("링크를 지우면 폴더의 활성 링크 여부를 200으로 돌려준다")
    void deletesSource() throws Exception {
        when(sourceDeleteUseCase.delete(1L, SOURCE_ID))
                .thenReturn(new SourceDeleteResponse(10L, false));

        mockMvc.perform(delete("/api/knowledge/sources/" + SOURCE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folderId").value(10))
                .andExpect(jsonPath("$.hasSource").value(false));

        verify(sourceDeleteUseCase).delete(1L, SOURCE_ID);
    }

    @Test
    @DisplayName("개념을 지우면 204를 준다")
    void deletesNode() throws Exception {
        mockMvc.perform(delete("/api/knowledge/nodes/" + SUBJECT_ID))
                .andExpect(status().isNoContent());

        verify(nodeDeleteUseCase).delete(1L, SUBJECT_ID);
    }

    @Test
    @DisplayName("목적은 혼자 지울 수 없어 409를 준다")
    void rejectsTopicDeletion() throws Exception {
        doThrow(new BusinessException(ErrorCode.KNOWLEDGE_TOPIC_NOT_DELETABLE))
                .when(nodeDeleteUseCase).delete(1L, TOPIC_ID);

        mockMvc.perform(delete("/api/knowledge/nodes/" + TOPIC_ID))
                .andExpect(status().isConflict());
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
