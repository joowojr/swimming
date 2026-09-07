package com.swimming.backend.knowledge.controller;

import com.swimming.backend.common.exception.GlobalExceptionHandler;
import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.RelationType;
import com.swimming.backend.knowledge.dto.in.GraphResponse;
import com.swimming.backend.knowledge.usecase.KnowledgeGraphUseCase;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KnowledgeGraphControllerTest {

    private static final UUID SOURCE_ID = UUID.fromString("0d0b1f22-0000-0000-0000-000000000001");
    private static final UUID SUBJECT_ID = UUID.fromString("3c220000-0000-0000-0000-000000000002");
    private static final UUID TOPIC_ID = UUID.fromString("9f1a0000-0000-0000-0000-000000000003");
    private static final Instant CREATED_AT = Instant.parse("2026-09-01T09:00:00Z");

    private KnowledgeGraphUseCase graphUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        graphUseCase = mock(KnowledgeGraphUseCase.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new KnowledgeGraphController(graphUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new AuthUserArgumentResolver(new AuthUser(1L, "user@example.com"))
                )
                .build();
    }

    private GraphResponse folderGraph() {
        return new GraphResponse(
                new GraphResponse.Root(null, GraphResponse.RootType.FOLDER, 10L, "Spring AI 공부"),
                List.of(
                        new GraphResponse.Node(
                                SOURCE_ID, NodeType.SOURCE, "Spring AI MCP Reference", CREATED_AT
                        ),
                        new GraphResponse.Node(SUBJECT_ID, NodeType.SUBJECT, "MCP", CREATED_AT),
                        new GraphResponse.Node(TOPIC_ID, NodeType.TOPIC, "MCP 서버 구현하기", CREATED_AT)
                ),
                List.of(
                        new GraphResponse.Edge(SOURCE_ID, SUBJECT_ID, RelationType.ABOUT),
                        new GraphResponse.Edge(SOURCE_ID, TOPIC_ID, RelationType.SUPPORTS),
                        new GraphResponse.Edge(TOPIC_ID, SUBJECT_ID, RelationType.INVOLVES)
                ),
                true
        );
    }

    @Test
    @DisplayName("Folder로 진입한 그래프를 200으로 돌려준다")
    void returnsFolderGraph() throws Exception {
        when(graphUseCase.ofFolder(1L, 10L, 20)).thenReturn(folderGraph());

        mockMvc.perform(get("/api/knowledge/graph").param("folderId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.root.type").value("FOLDER"))
                .andExpect(jsonPath("$.root.nodeId").doesNotExist())
                .andExpect(jsonPath("$.root.id").value(10))
                .andExpect(jsonPath("$.nodes[0].type").value("SOURCE"))
                .andExpect(jsonPath("$.nodes[0].createdAt").value("2026-09-01T09:00:00Z"))
                .andExpect(jsonPath("$.edges[0].kind").value("ABOUT"))
                .andExpect(jsonPath("$.edges[2].kind").value("INVOLVES"))
                .andExpect(jsonPath("$.truncated").value(true));

        verify(graphUseCase).ofFolder(1L, 10L, 20);
    }

    @Test
    @DisplayName("초기 노드 수 상한을 지정할 수 있다")
    void acceptsLimit() throws Exception {
        when(graphUseCase.ofFolder(eq(1L), eq(10L), eq(5))).thenReturn(folderGraph());

        mockMvc.perform(get("/api/knowledge/graph").param("folderId", "10").param("limit", "5"))
                .andExpect(status().isOk());

        verify(graphUseCase).ofFolder(1L, 10L, 5);
    }

    @Test
    @DisplayName("상한을 넘긴 limit은 조회하기 전에 거부한다")
    void rejectsTooLargeLimit() throws Exception {
        mockMvc.perform(get("/api/knowledge/graph").param("folderId", "10").param("limit", "51"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(graphUseCase);
    }

    @Test
    @DisplayName("노드 확장은 depth를 주지 않으면 1-hop이다")
    void expandsOneHopByDefault() throws Exception {
        when(graphUseCase.ofNode(anyLong(), any(), anyInt())).thenReturn(new GraphResponse(
                new GraphResponse.Root(SUBJECT_ID, GraphResponse.RootType.SUBJECT, null, "MCP"),
                List.of(), List.of(), false
        ));

        mockMvc.perform(get("/api/knowledge/nodes/" + SUBJECT_ID + "/graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.root.type").value("SUBJECT"))
                .andExpect(jsonPath("$.root.id").doesNotExist());

        verify(graphUseCase).ofNode(1L, SUBJECT_ID, 1);
    }

    @Test
    @DisplayName("더 보기는 depth=2로 부른다")
    void expandsTwoHops() throws Exception {
        when(graphUseCase.ofNode(anyLong(), any(), anyInt())).thenReturn(new GraphResponse(
                new GraphResponse.Root(SUBJECT_ID, GraphResponse.RootType.SUBJECT, null, "MCP"),
                List.of(), List.of(), false
        ));

        mockMvc.perform(get("/api/knowledge/nodes/" + SUBJECT_ID + "/graph").param("depth", "2"))
                .andExpect(status().isOk());

        verify(graphUseCase).ofNode(1L, SUBJECT_ID, 2);
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
