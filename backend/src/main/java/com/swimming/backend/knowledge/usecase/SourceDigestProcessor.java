package com.swimming.backend.knowledge.usecase;

import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.SourceProcessingStatus;
import com.swimming.backend.knowledge.dto.in.SourceDigestResponse;
import com.swimming.backend.knowledge.dto.out.SourceDigestInput;
import com.swimming.backend.knowledge.dto.out.SourceDigestResult;
import com.swimming.backend.knowledge.dto.out.ResolvedNode;
import com.swimming.backend.knowledge.service.data.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.data.KnowledgeSourceService;
import com.swimming.backend.knowledge.service.graph.CategoryAssignmentService;
import com.swimming.backend.knowledge.service.graph.SourceGraphWriter;
import com.swimming.backend.knowledge.service.llm.SourceDigestService;
import com.swimming.backend.knowledge.service.graph.NodeResolutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * 저장해 둔 Source 하나를 AI로 소화한다.
 *
 * <p>소화가 실패해도 원문은 그대로 둔다. 다만 다시 부르는 것은 {@code PENDING} 뿐이라
 * 실패한 Source는 그대로 남는다. 재시도 경로가 필요해지면 그때 따로 연다.
 *
 * <p>LLM 호출을 트랜잭션 안에 두지 않는다. 모델 응답을 기다리는 동안 커넥션을 잡고 있게
 * 되기 때문이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SourceDigestProcessor {

    /** 프롬프트나 출력 스키마를 바꾸면 올린다. 어떤 기준으로 분석했는지 구분하기 위해서다. */
    private static final int ANALYSIS_VERSION = 5;
    private static final int MAX_CONTENT_LENGTH_WITHOUT_DIGESTION = 100;

    private final KnowledgeSourceService sourceService;
    private final KnowledgeNodeService nodeService;
    private final SourceDigestService digestService;
    private final NodeResolutionService nodeResolutionService;
    private final SourceGraphWriter graphWriter;
    private final CategoryAssignmentService categoryAssignmentService;

    public SourceDigestResponse digest(Long userId, UUID sourceId) {
        KnowledgeSource source = sourceService.getOwned(sourceId, userId);

        // 신규 Source만 처리한다. 이미 소화한 것을 매번 다시 부르지 않는다.
        if (source.getProcessingStatus() != SourceProcessingStatus.PENDING) {
            return SourceDigestResponse.of(source, null);
        }

        if (!StringUtils.hasText(source.getContent())) {
            source.failDigestion(ErrorCode.SOURCE_EMPTY_CONTENT.name(), false);
            return SourceDigestResponse.of(sourceService.save(source), null);
        }

        if (source.getContent().length() <= MAX_CONTENT_LENGTH_WITHOUT_DIGESTION) {
            source.completeWithoutDigestion();
            return SourceDigestResponse.of(sourceService.save(source), null);
        }

        source.startDigestion();

        SourceDigestResult result;
        try {
            result = requireTopic(digestService.digest(new SourceDigestInput(
                    source.getNode().getTitle(),
                    source.getUrl(),
                    source.getContent(),
                    // Topic은 재사용 판정을 하지 않는다. 이름이 문서마다 갈라지지 않도록
                    // 이미 쓰던 표현만 참고로 보여 준다.
                    nodeService.findTitles(userId, NodeType.TOPIC)
            )));
        } catch (RuntimeException exception) {
            return fail(
                    source,
                    ErrorCode.SOURCE_DIGEST_FAILURE,
                    ErrorCode.SOURCE_DIGEST_NON_RETRYABLE_FAILURE,
                    exception
            );
        }

        List<ResolvedNode> resolvedSubjects;
        try {
            resolvedSubjects = nodeResolutionService.resolveSubjects(
                    source, result.summary(), result.subjects()
            );
            result = new SourceDigestResult(
                    result.summary(),
                    result.category(),
                    result.topic(),
                    resolvedSubjects.stream().map(item -> item.node().getTitle()).toList()
            );
        } catch (RuntimeException exception) {
            return fail(
                    source,
                    ErrorCode.SOURCE_DIGEST_RESOLUTION_FAILURE,
                    ErrorCode.SOURCE_DIGEST_RESOLUTION_NON_RETRYABLE_FAILURE,
                    exception
            );
        }

        source.completeDigestion(result.summary(), ANALYSIS_VERSION);
        KnowledgeSource saved = sourceService.save(source);

        writeGraph(saved, result, resolvedSubjects);
        // 배정 실패는 안에서 삼킨다. 소화 결과와 응답에 영향을 주지 않는다.
        categoryAssignmentService.assign(saved, result);

        return SourceDigestResponse.of(saved, result);
    }

    private SourceDigestResponse fail(
            KnowledgeSource source,
            ErrorCode retryableFailure,
            ErrorCode nonRetryableFailure,
            RuntimeException exception
    ) {
        log.info(
                "[source-digest] failed sourceId={} url={} reason={}",
                source.getId(), source.getUrl(), exception.toString()
        );

        boolean retryable = isRetryable(exception);
        ErrorCode failure = retryable ? retryableFailure : nonRetryableFailure;
        source.failDigestion(failure.name(), retryable);
        return SourceDigestResponse.of(sourceService.save(source), null);
    }

    /** 잘못된 요청처럼 다시 보내도 달라지지 않는 AI 오류는 재시도 대상으로 노출하지 않는다. */
    private boolean isRetryable(RuntimeException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof NonTransientAiException) {
                return false;
            }
            if (cause instanceof TransientAiException) {
                return true;
            }
            cause = cause.getCause();
        }
        return true;
    }

    /**
     * Topic 없는 결과는 소화 실패로 본다.
     *
     * <p>프롬프트가 항상 하나를 쓰게 하지만 모델이 지키지 않을 수 있다. 그대로 두면 Topic이
     * 비어 있는 {@code COMPLETED} Source가 남는데, 다시 소화하는 경로가 없어 영영 그 상태다.
     * 실패로 돌리면 위의 {@code catch}가 원문을 남기고 상태만 {@code FAILED}로 바꾼다.
     */
    private SourceDigestResult requireTopic(SourceDigestResult result) {
        if (result == null || !StringUtils.hasText(result.topic())) {
            throw new IllegalStateException("digest result has no topic");
        }

        return result;
    }

    /**
     * 그래프 반영 실패로 소화 자체를 되돌리지 않는다. 모델은 이미 답했고 요약도 저장했는데
     * 여기서 실패로 표시하면 그 결과를 버리게 된다.
     *
     * <p>지금은 로그로만 남는다. 반영이 빠진 Source를 다시 이어 붙이는 경로가 필요해지면
     * 그때 상태를 따로 둔다.
     */
    private void writeGraph(
            KnowledgeSource source,
            SourceDigestResult result,
            List<ResolvedNode> resolvedSubjects
    ) {
        try {
            graphWriter.write(source, result, resolvedSubjects);
        } catch (RuntimeException exception) {
            log.warn(
                    "[source-digest] graph write failed sourceId={} reason={}",
                    source.getId(), exception.toString()
            );
        }
    }
}
