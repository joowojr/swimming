package com.swimming.backend.knowledge.service.llm;

import com.swimming.backend.common.config.llm.ChatOptionsFactory;
import com.swimming.backend.common.logging.LlmUsageLogger;
import com.swimming.backend.common.prompt.PromptKey;
import com.swimming.backend.common.prompt.PromptRepository;
import com.swimming.backend.knowledge.dto.out.NodeResolutionInputV2;
import com.swimming.backend.knowledge.dto.out.NodeResolutionResultV2;
import com.swimming.backend.knowledge.prompt.NodeResolutionInputV2Serializer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NodeResolutionLlmService {

    private static final String LOG_FEATURE = "node-resolution";

    private final ChatClient chatClient;
    private final PromptRepository promptRepository;
    private final ChatOptionsFactory chatOptionsFactory;
    private final LlmUsageLogger usageLogger;

    public NodeResolutionLlmService(
            ChatClient chatClient,
            PromptRepository promptRepository,
            ChatOptionsFactory chatOptionsFactory,
            LlmUsageLogger usageLogger
    ) {
        this.chatClient = chatClient;
        this.promptRepository = promptRepository;
        this.chatOptionsFactory = chatOptionsFactory;
        this.usageLogger = usageLogger;
    }

    public List<NodeResolutionLlmDecision> resolve(NodeResolutionLlmRequest request) {
        IndexedRequest indexedRequest = toIndexedRequest(request);
        NodeResolutionInputV2 input = indexedRequest.input();
        String systemPrompt = promptRepository.get(PromptKey.NODE_RESOLUTION_V2);
        String userMessage = NodeResolutionInputV2Serializer.serialize(input);

        var response = chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .options(chatOptionsFactory.create())
                .call()
                .responseEntity(
                        NodeResolutionResultV2.class,
                        spec -> spec.useProviderStructuredOutput().validateSchema()
                );

        usageLogger.log(
                LOG_FEATURE + "-v2",
                response.getResponse(),
                systemPrompt,
                userMessage,
                "candidates=%d".formatted(input.candidates().size())
        );
        return translate(response.getEntity(), indexedRequest);
    }

    /** 실제 식별자를 LLM 프롬프트에서만 사용하는 짧은 C/R 번호로 바꾼다. */
    static IndexedRequest toIndexedRequest(NodeResolutionLlmRequest request) {
        List<NodeResolutionInputV2.Candidate> indexedCandidates = new ArrayList<>();
        Map<Integer, String> candidateKeyByIndex = new LinkedHashMap<>();
        Map<UUID, Integer> reuseIndexBySubjectId = new LinkedHashMap<>();
        Map<Integer, UUID> subjectIdByReuseIndex = new LinkedHashMap<>();
        int nextReuseIndex = 1;

        for (int candidateIndex = 0; candidateIndex < request.candidates().size(); candidateIndex++) {
            NodeResolutionLlmRequest.Candidate candidate = request.candidates().get(candidateIndex);
            int promptCandidateIndex = candidateIndex + 1;
            candidateKeyByIndex.put(promptCandidateIndex, candidate.key());

            List<NodeResolutionInputV2.Match> indexedMatches = new ArrayList<>();
            for (NodeResolutionLlmRequest.ReusableSubject match : candidate.matches()) {
                Integer reuseIndex = reuseIndexBySubjectId.get(match.subjectId());
                if (reuseIndex == null) {
                    reuseIndex = nextReuseIndex++;
                    reuseIndexBySubjectId.put(match.subjectId(), reuseIndex);
                    subjectIdByReuseIndex.put(reuseIndex, match.subjectId());
                }
                indexedMatches.add(new NodeResolutionInputV2.Match(reuseIndex, match.title()));
            }
            indexedCandidates.add(new NodeResolutionInputV2.Candidate(
                    promptCandidateIndex,
                    candidate.value(),
                    indexedMatches
            ));
        }

        List<NodeResolutionInputV2.ContextSubject> indexedContextSubjects = new ArrayList<>();
        for (NodeResolutionLlmRequest.ReusableSubject subject : request.contextSubjects()) {
            if (reuseIndexBySubjectId.containsKey(subject.subjectId())) {
                continue;
            }
            int reuseIndex = nextReuseIndex++;
            reuseIndexBySubjectId.put(subject.subjectId(), reuseIndex);
            subjectIdByReuseIndex.put(reuseIndex, subject.subjectId());
            indexedContextSubjects.add(new NodeResolutionInputV2.ContextSubject(
                    reuseIndex, subject.title()));
        }

        return new IndexedRequest(
                new NodeResolutionInputV2(
                        request.summary(), indexedCandidates, indexedContextSubjects),
                candidateKeyByIndex,
                subjectIdByReuseIndex
        );
    }

    /** LLM의 C/R 번호를 호출자가 이해하는 candidate key와 Subject ID로 되돌린다. */
    static List<NodeResolutionLlmDecision> translate(
            NodeResolutionResultV2 result,
            IndexedRequest indexedRequest
    ) {
        if (result == null || result.decisions() == null) {
            throw new IllegalStateException("node resolution v2 result is empty");
        }

        Map<String, NodeResolutionLlmDecision> decisionsByCandidateKey = new LinkedHashMap<>();
        for (NodeResolutionResultV2.Decision decision : result.decisions()) {
            if (decision == null || decision.reuseIndex() < 0) {
                continue;
            }
            String candidateKey = indexedRequest.candidateKeyByIndex()
                    .get(decision.candidateIndex());
            if (candidateKey == null || decisionsByCandidateKey.containsKey(candidateKey)) {
                continue;
            }

            if (decision.reuseIndex() > 0) {
                UUID subjectId = indexedRequest.subjectIdByReuseIndex()
                        .get(decision.reuseIndex());
                if (subjectId != null) {
                    decisionsByCandidateKey.put(
                            candidateKey,
                            NodeResolutionLlmDecision.reuse(candidateKey, subjectId)
                    );
                }
                continue;
            }

            if (StringUtils.hasText(decision.value())) {
                decisionsByCandidateKey.put(
                        candidateKey,
                        NodeResolutionLlmDecision.create(candidateKey, decision.value().strip())
                );
            }
        }

        if (decisionsByCandidateKey.isEmpty()) {
            throw new IllegalStateException("node resolution v2 returned no usable decision");
        }
        return List.copyOf(decisionsByCandidateKey.values());
    }

    record IndexedRequest(
            NodeResolutionInputV2 input,
            Map<Integer, String> candidateKeyByIndex,
            Map<Integer, UUID> subjectIdByReuseIndex
    ) {
    }
}
