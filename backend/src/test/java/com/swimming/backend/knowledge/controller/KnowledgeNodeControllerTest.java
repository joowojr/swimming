package com.swimming.backend.knowledge.controller;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.common.exception.GlobalExceptionHandler;
import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.dto.in.NodeDetailResponse;
import com.swimming.backend.knowledge.dto.in.NodeRef;
import com.swimming.backend.knowledge.usecase.NodeUseCase;
import com.swimming.backend.knowledge.exception.CategoryTitleDuplicateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KnowledgeNodeControllerTest {

    private static final UUID SOURCE_ID = UUID.fromString("0d0b1f22-0000-0000-0000-000000000001");
    private static final UUID SUBJECT_ID = UUID.fromString("3c220000-0000-0000-0000-000000000002");
    private static final UUID TOPIC_ID = UUID.fromString("9f1a0000-0000-0000-0000-000000000003");
    private static final Instant CREATED_AT = Instant.parse("2026-09-01T09:00:00Z");

    private NodeUseCase nodeUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        nodeUseCase = mock(NodeUseCase.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new KnowledgeNodeController(nodeUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new AuthUserArgumentResolver(new AuthUser(1L, "user@example.com"))
                )
                .build();
    }

    @Test
    @DisplayName("Topic 상세는 문서 하나와 걸치는 개념을 돌려준다")
    void returnsNodeDetail() throws Exception {
        when(nodeUseCase.get(1L, TOPIC_ID)).thenReturn(new NodeDetailResponse(
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
        when(nodeUseCase.get(1L, SOURCE_ID))
                .thenThrow(new BusinessException(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND));

        mockMvc.perform(get("/api/knowledge/nodes/" + SOURCE_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("개념을 지우면 204를 준다")
    void deletesNode() throws Exception {
        mockMvc.perform(delete("/api/knowledge/nodes/" + SUBJECT_ID))
                .andExpect(status().isNoContent());

        verify(nodeUseCase).delete(1L, SUBJECT_ID);
    }

    @Test
    @DisplayName("목적은 혼자 지울 수 없어 409를 준다")
    void rejectsTopicDeletion() throws Exception {
        doThrow(new BusinessException(ErrorCode.KNOWLEDGE_TOPIC_NOT_DELETABLE))
                .when(nodeUseCase).delete(1L, TOPIC_ID);

        mockMvc.perform(delete("/api/knowledge/nodes/" + TOPIC_ID))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("제목 수정은 수정한 노드 참조를 200으로 반환한다")
    void updatesTitle() throws Exception {
        when(nodeUseCase.updateTitle(1L, TOPIC_ID, "새 목적"))
                .thenReturn(new NodeRef(TOPIC_ID, "새 목적"));

        mockMvc.perform(patch("/api/knowledge/nodes/" + TOPIC_ID + "/title")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"새 목적\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeId").value(TOPIC_ID.toString()))
                .andExpect(jsonPath("$.titleRenamedAt").doesNotExist())
                .andExpect(jsonPath("$.title").value("새 목적"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"title\":null}", "{\"title\":\"\"}", "{\"title\":\"   \"}"})
    @DisplayName("제목 누락과 빈 제목은 ProblemDetail 400으로 거절한다")
    void rejectsBlankTitle(String body) throws Exception {
        mockMvc.perform(patch("/api/knowledge/nodes/" + TOPIC_ID + "/title")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    @DisplayName("제목은 500자까지 허용하고 501자는 거절한다")
    void validatesTitleLength() throws Exception {
        String title = "가".repeat(500);
        when(nodeUseCase.updateTitle(1L, TOPIC_ID, title)).thenReturn(new NodeRef(TOPIC_ID, title));
        mockMvc.perform(patch("/api/knowledge/nodes/" + TOPIC_ID + "/title")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/knowledge/nodes/" + TOPIC_ID + "/title")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + title + "가\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 전체_병합은_대상_카테고리_ID를_전달하고_대상_노드를_반환한다() throws Exception {
        UUID targetId = UUID.randomUUID();
        when(nodeUseCase.merge(1L, TOPIC_ID, targetId, null)).thenReturn(new NodeRef(targetId, "대상 분류"));
        mockMvc.perform(put("/api/knowledge/nodes/" + TOPIC_ID + "/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetCategoryId\":\"" + targetId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeId").value(targetId.toString()));
        verify(nodeUseCase).merge(1L, TOPIC_ID, targetId, null);
    }

    @Test
    void 문서_ID를_전달하면_단건_이동_유스케이스를_호출한다() throws Exception {
        UUID categoryId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(nodeUseCase.merge(1L, categoryId, targetId, SOURCE_ID)).thenReturn(new NodeRef(targetId, "대상 분류"));
        mockMvc.perform(put("/api/knowledge/nodes/" + categoryId + "/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetCategoryId\":\"" + targetId + "\",\"sourceId\":\"" + SOURCE_ID + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nodeId").value(targetId.toString()));
        verify(nodeUseCase).merge(1L, categoryId, targetId, SOURCE_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"targetCategoryId\":null}", "{\"targetCategoryId\":\"invalid\"}",
            "{\"targetCategoryId\":\"99999999-9999-9999-9999-999999999999\",\"sourceId\":\"invalid\"}"})
    @DisplayName("병합 대상 누락과 잘못된 UUID는 400으로 거절한다")
    void validatesMergeRequest(String body) throws Exception {
        mockMvc.perform(put("/api/knowledge/nodes/" + TOPIC_ID + "/merge")
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    }

    @Test
    void 중복_이름_수정은_409로_거절한다() throws Exception {
        UUID targetId = UUID.randomUUID();
        when(nodeUseCase.updateTitle(1L, TOPIC_ID, "중복"))
                .thenThrow(new CategoryTitleDuplicateException(new NodeRef(targetId, "기존 이름")));
        mockMvc.perform(patch("/api/knowledge/nodes/" + TOPIC_ID + "/title")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"중복\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("KNOWLEDGE_CATEGORY_TITLE_DUPLICATE"))
                .andExpect(jsonPath("$.targetCategory.nodeId").value(targetId.toString()))
                .andExpect(jsonPath("$.targetCategory.title").value("기존 이름"));
    }

    @Test
    void 기존_PUT_제목_수정_경로는_허용하지_않는다() throws Exception {
        mockMvc.perform(put("/api/knowledge/nodes/" + TOPIC_ID + "/title")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"수정\"}"))
                .andExpect(status().isMethodNotAllowed());
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
