package com.swimming.backend.knowledge.live;

import com.swimming.backend.common.config.llm.LlmProperties;
import com.swimming.backend.common.config.llm.LlmProvider;
import com.swimming.backend.common.config.llm.OllamaChatOptionsFactory;
import com.swimming.backend.common.config.llm.OpenAiChatOptionsFactory;
import com.swimming.backend.common.logging.LlmUsageLogger;
import com.swimming.backend.common.prompt.PromptKey;
import com.swimming.backend.common.prompt.PromptProperties;
import com.swimming.backend.common.prompt.ResourcePromptRepository;
import com.swimming.backend.knowledge.config.KnowledgeDigestProperties;
import com.swimming.backend.knowledge.config.KnowledgeFetchProperties;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeRelation;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.SourceProcessingStatus;
import com.swimming.backend.knowledge.domain.RelationType;
import com.swimming.backend.knowledge.dto.in.SourceCollectRequest;
import com.swimming.backend.knowledge.dto.in.SourceCollectResponse;
import com.swimming.backend.knowledge.dto.in.NodeRef;
import com.swimming.backend.knowledge.dto.in.SourceResponse;
import com.swimming.backend.knowledge.repository.InMemoryKnowledgeRepositories;
import com.swimming.backend.knowledge.service.crawl.HtmlToMarkdownConverter;
import com.swimming.backend.knowledge.service.crawl.RenderedPageFetcher;
import com.swimming.backend.knowledge.service.crawl.WebFetchService;
import com.swimming.backend.knowledge.service.data.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.data.KnowledgeRelationService;
import com.swimming.backend.knowledge.service.data.KnowledgeSourceService;
import com.swimming.backend.knowledge.service.graph.NodeResolver;
import com.swimming.backend.knowledge.service.SourceGraphReader;
import com.swimming.backend.knowledge.service.graph.SourceGraphWriter;
import com.swimming.backend.knowledge.service.llm.DigestContextTrimmer;
import com.swimming.backend.knowledge.service.llm.SourceDigestProcessor;
import com.swimming.backend.knowledge.service.llm.SourceDigestService;
import com.swimming.backend.knowledge.usecase.SourceCollectUseCase;
import com.swimming.backend.folder.service.FolderService;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 링크를 넣어 수집부터 그래프 반영까지 돌려보고 결과를 눈으로 확인한다.
 *
 * <p>문서 하나의 소화 품질뿐 아니라 <b>여러 문서를 함께 넣었을 때 그래프가 어떤 모양이
 * 되는지</b>를 본다. Subject가 실제로 재사용되는지, 같은 목적이 다른 이름의 Topic으로
 * 갈라지지 않는지는 링크를 하나만 넣어서는 드러나지 않는다.
 *
 * <pre>
 * ./gradlew digestUrl -Purls="https://tech.kakao.com/posts/777"
 * ./gradlew digestUrl -Purls="a,b,c"    # 링크를 여러 개 넣어야 재사용이 보인다
 * ./gradlew digestUrl -Purls="https://a.com,https://b.com" -PllmModel=gpt-5.6-luna
 * ./gradlew digestUrl -Purls="..." -PllmProvider=ollama -PllmModel=qwen3:8b
 * </pre>
 *
 * OpenAI 는 {@code OPENAI_API_KEY} 또는 {@code src/test/.env.test} 가 필요하다.
 */
@Tag("digest-live")
@Timeout(value = 20, unit = TimeUnit.MINUTES)
class SourceDigestPlaygroundTest {

    private static final Long USER_ID = 1L;
    private static final Long FOLDER_ID = 10L;
    private static final String DEFAULT_URL =
            "https://docs.spring.io/spring-ai/reference/api/chatclient.html";

    @Test
    @DisplayName("-Purls 로 넘긴 링크를 수집하고 소화해 결과를 보여준다")
    void digestGivenUrls() throws Exception {
        List<String> urls = Arrays.stream(System.getProperty("urls", DEFAULT_URL).split(","))
                .map(String::strip).filter(url -> !url.isEmpty()).toList();

        LlmProvider provider = LlmProvider.valueOf(
                System.getProperty("llmProvider", "openai").toUpperCase().replace('-', '_'));
        String model = System.getProperty("llmModel", provider == LlmProvider.OLLAMA
                ? "qwen3:8b" : "gpt-5.6-luna");

        var sources = new InMemoryKnowledgeRepositories.Sources();
        var nodes = new InMemoryKnowledgeRepositories.Nodes();
        var relations = new InMemoryKnowledgeRepositories.Relations();
        var sourceService = new KnowledgeSourceService(sources);
        var nodeService = new KnowledgeNodeService(nodes);

        var digestUseCase = new SourceDigestProcessor(
                sourceService,
                nodeService,
                digestService(provider, model),
                new SourceGraphWriter(
                        nodeService,
                        new NodeResolver(nodes),
                        new KnowledgeRelationService(relations)
                )
        );
        var collectUseCase = new SourceCollectUseCase(
                fetchService(),
                mock(FolderService.class),
                sourceService,
                digestUseCase,
                new SourceGraphReader(relations, nodes)
        );

        System.out.printf("%n모델: %s (%s) | 링크 %d개%n", model, provider, urls.size());

        // 저장 한 번으로 수집과 소화가 모두 끝난다.
        long startedAt = System.currentTimeMillis();
        SourceCollectResponse collected = collectUseCase.collect(
                USER_ID, FOLDER_ID, new SourceCollectRequest(urls));
        long elapsed = System.currentTimeMillis() - startedAt;

        System.out.printf("링크 %d개 저장에 %.1f초 (링크당 %.1f초)%n",
                urls.size(), elapsed / 1000.0, elapsed / 1000.0 / urls.size());

        Path outDir = Path.of("build", "digest-live");
        Files.createDirectories(outDir);

        for (SourceCollectResponse.Item item : collected.items()) {
            System.out.println("\n" + "=".repeat(100));
            System.out.println(item.url());
            System.out.println("=".repeat(100));

            if (item.source() == null) {
                System.out.printf("수집 실패: %s%n", item.reason());
                continue;
            }

            SourceResponse card = item.source();
            KnowledgeSource source = sources.findById(card.sourceId()).orElseThrow();

            System.out.printf("제목      : %s%n", card.title());
            System.out.printf("본문      : %d자%n", source.getContent().length());
            System.out.printf("처리      : %s / %s%n", item.result(), card.status());

            if (card.status() != SourceProcessingStatus.COMPLETED) {
                continue;
            }

            System.out.printf("%nsummary   : %s%n", card.summary());
            System.out.printf("topic     : %s%n",
                    card.topic() == null ? "(없음)" : card.topic().title());
            System.out.printf("subjects  : %s%n",
                    card.subjects().stream().map(NodeRef::title).toList());

            Files.writeString(
                    outDir.resolve(card.sourceId() + ".md"),
                    "# " + card.title() + "\n\n"
                            + "- url: " + card.url() + "\n"
                            + "- summary: " + card.summary() + "\n"
                            + "- topic: " + (card.topic() == null ? "" : card.topic().title()) + "\n"
                            + "- subjects: "
                            + card.subjects().stream().map(NodeRef::title).toList() + "\n\n"
                            + source.getContent()
            );
        }

        List<UUID> sourceIds = collected.items().stream()
                .map(SourceCollectResponse.Item::source)
                .filter(java.util.Objects::nonNull)
                .map(SourceResponse::sourceId)
                .toList();

        String report = graphReport(sources, nodes, relations, sourceIds);
        System.out.println(report);
        Files.writeString(outDir.resolve("graph.md"), report);

        System.out.println("저장 위치: " + outDir.toAbsolutePath());
        assertThat(collected.items()).hasSize(urls.size());
    }

    /**
     * 소화가 끝난 뒤 그래프가 어떤 모양이 됐는지 한 번에 본다.
     *
     * <p>문서별 출력만으로는 파이프라인의 핵심인 <b>재사용</b>이 보이지 않는다. Subject가
     * 문서마다 새로 생기고 있는지, 같은 목적이 다른 이름의 Topic으로 갈라졌는지는 그래프를
     * 통째로 놓고 봐야 드러난다.
     */
    private String graphReport(
            InMemoryKnowledgeRepositories.Sources sources,
            InMemoryKnowledgeRepositories.Nodes nodes,
            InMemoryKnowledgeRepositories.Relations relations,
            List<UUID> sourceIds
    ) {
        List<KnowledgeSource> saved = sources.findAllByIds(sourceIds);
        List<KnowledgeNode> subjects = nodes.findAllByUserIdAndNodeType(USER_ID, NodeType.SUBJECT);
        List<KnowledgeNode> topics = nodes.findAllByUserIdAndNodeType(USER_ID, NodeType.TOPIC);

        Map<UUID, String> titles = new LinkedHashMap<>();
        saved.forEach(source -> titles.put(source.getNode().getId(), source.getNode().getTitle()));
        subjects.forEach(node -> titles.put(node.getId(), node.getTitle()));
        topics.forEach(node -> titles.put(node.getId(), node.getTitle()));

        StringBuilder out = new StringBuilder();
        out.append("\n").append("=".repeat(100)).append("\n");
        out.append("# 그래프 (문서 ").append(saved.size())
                .append(" / Subject ").append(subjects.size())
                .append(" / Topic ").append(topics.size()).append(")\n");
        out.append("=".repeat(100)).append("\n");

        appendSubjectReuse(out, relations, titles, subjects, saved.size());
        appendTopics(out, relations, titles, topics);
        appendRelationCounts(out, relations, titles);
        appendSubgraphs(out, relations, titles, saved);

        return out.toString();
    }

    /**
     * Subject 하나를 몇 개의 문서가 함께 가리키는지 본다. 전부 문서 1개씩이면 개념 이름이
     * 문서마다 갈라지고 있다는 뜻이고, 그 경우 §11의 관계 조회가 성립하지 않는다.
     */
    private void appendSubjectReuse(
            StringBuilder out,
            InMemoryKnowledgeRepositories.Relations relations,
            Map<UUID, String> titles,
            List<KnowledgeNode> subjects,
            int sourceCount
    ) {
        out.append("\n## Subject 재사용\n\n");

        if (subjects.isEmpty()) {
            out.append("(없음)\n");
            return;
        }

        List<Map.Entry<KnowledgeNode, List<String>>> byUsage = subjects.stream()
                .map(subject -> Map.entry(
                        subject, incoming(relations, titles, subject.getId(), RelationType.ABOUT)))
                .sorted(Comparator
                        .comparingInt((Map.Entry<KnowledgeNode, List<String>> entry) -> entry.getValue().size())
                        .reversed())
                .toList();

        for (Map.Entry<KnowledgeNode, List<String>> entry : byUsage) {
            out.append("- %-28s 문서 %d개  %s%n".formatted(
                    entry.getKey().getTitle(),
                    entry.getValue().size(),
                    String.join(" / ", entry.getValue())
            ));
        }

        long shared = byUsage.stream().filter(entry -> entry.getValue().size() > 1).count();
        out.append("%n→ 문서 %d개가 Subject %d개를 만들었고 그중 %d개를 나눠 쓴다.%n"
                .formatted(sourceCount, subjects.size(), shared));

        if (shared == 0 && sourceCount > 1) {
            out.append("→ 공유된 개념이 하나도 없다. 링크가 서로 무관하거나 이름이 갈라지고 있다.%n"
                    .formatted());
        }
    }

    /**
     * Topic은 Source당 하나씩 새로 만든다. 재사용 판정을 하지 않으므로, 같은 목적이 서로
     * 다른 이름으로 적혔는지는 여기서 눈으로 봐야 한다.
     */
    private void appendTopics(
            StringBuilder out,
            InMemoryKnowledgeRepositories.Relations relations,
            Map<UUID, String> titles,
            List<KnowledgeNode> topics
    ) {
        out.append("\n## Topic (Source당 1개, 재사용 판정 없음)\n\n");

        if (topics.isEmpty()) {
            out.append("(없음 — 적용 목적을 담은 문서가 아니었다)\n");
            return;
        }

        for (KnowledgeNode topic : topics) {
            out.append("- %-32s ← %s%n".formatted(
                    topic.getTitle(),
                    String.join(" / ", incoming(relations, titles, topic.getId(), RelationType.SUPPORTS))
            ));
        }

        out.append("%n→ 이름이 서로 비슷하면서 다르게 적혔다면 참고 맥락이 덜 먹힌 것이다.%n"
                .formatted());
    }

    private void appendRelationCounts(
            StringBuilder out,
            InMemoryKnowledgeRepositories.Relations relations,
            Map<UUID, String> titles
    ) {
        out.append("\n## 관계\n\n");

        for (RelationType relationType : RelationType.values()) {
            out.append("- %-10s %d개%n".formatted(
                    relationType,
                    relations.findAllByFromNodeIdIn(titles.keySet(), List.of(relationType)).size()
            ));
        }
    }

    /** §10 Graph Browser가 문서를 눌렀을 때 보여줄 모양 그대로. */
    private void appendSubgraphs(
            StringBuilder out,
            InMemoryKnowledgeRepositories.Relations relations,
            Map<UUID, String> titles,
            List<KnowledgeSource> saved
    ) {
        out.append("\n## 문서별 서브그래프\n");

        for (KnowledgeSource source : saved) {
            UUID sourceNodeId = source.getNode().getId();

            out.append("%n### %s%n".formatted(source.getNode().getTitle()));
            out.append("  ABOUT    → %s%n".formatted(
                    String.join(", ", outgoing(relations, titles, sourceNodeId, RelationType.ABOUT))));

            for (KnowledgeRelation supports : relations.findAllByFromNodeIdIn(
                    List.of(sourceNodeId), List.of(RelationType.SUPPORTS))
            ) {
                out.append("  SUPPORTS → %s%n".formatted(titles.get(supports.getToNodeId())));
                out.append("             INVOLVES → %s%n".formatted(String.join(
                        ", ", outgoing(relations, titles, supports.getToNodeId(), RelationType.INVOLVES))));
            }
        }
    }

    /** 이 노드를 가리키는 노드들의 이름. Subject를 다루는 문서를 찾을 때처럼 거꾸로 본다. */
    private List<String> incoming(
            InMemoryKnowledgeRepositories.Relations relations,
            Map<UUID, String> titles,
            UUID toNodeId,
            RelationType relationType
    ) {
        return names(titles, relations.findAllByToNodeIdIn(List.of(toNodeId), List.of(relationType))
                .stream().map(KnowledgeRelation::getFromNodeId).toList());
    }

    private List<String> outgoing(
            InMemoryKnowledgeRepositories.Relations relations,
            Map<UUID, String> titles,
            UUID fromNodeId,
            RelationType relationType
    ) {
        return names(titles, relations.findAllByFromNodeIdIn(List.of(fromNodeId), List.of(relationType))
                .stream().map(KnowledgeRelation::getToNodeId).toList());
    }

    private List<String> names(Map<UUID, String> titles, List<UUID> nodeIds) {
        List<String> result = new ArrayList<>(nodeIds.size());
        nodeIds.forEach(nodeId -> result.add(titles.getOrDefault(nodeId, nodeId.toString())));
        return result;
    }

    private WebFetchService fetchService() {
        var properties = new KnowledgeFetchProperties(
                4, Duration.ofSeconds(15), 4 * 1024 * 1024, 80_000, 300,
                "SwimmingBot/0.1 (+https://swimming.app)",
                new KnowledgeFetchProperties.Render(true, Duration.ofSeconds(20), 1000)
        );
        return new WebFetchService(
                properties,
                new HtmlToMarkdownConverter(),
                Optional.of(new RenderedPageFetcher(properties))
        );
    }

    private SourceDigestService digestService(LlmProvider provider, String model) {
        var promptRepository = new ResourcePromptRepository(
                new PromptProperties(
                        Map.of(
                                PromptKey.TASK_ORGANIZER.configName(),
                                "classpath:prompts/task-organizer/classify.md",
                                PromptKey.TASK_EXTRACTOR.configName(),
                                "classpath:prompts/task-organizer/extract.md",
                                PromptKey.SOURCE_DIGEST.configName(),
                                "classpath:prompts/knowledge/digest.md"
                        ),
                        Map.of(
                                "splitting", "classpath:prompts/task-organizer/_splitting.md",
                                "titles", "classpath:prompts/task-organizer/_titles.md"
                        )
                ),
                new DefaultResourceLoader()
        );

        ChatModel chatModel = provider == LlmProvider.OLLAMA
                ? OllamaChatModel.builder()
                        .ollamaApi(OllamaApi.builder()
                                .baseUrl(System.getenv().getOrDefault(
                                        "OLLAMA_BASE_URL", "http://localhost:11434"))
                                .build())
                        .build()
                : OpenAiChatModel.builder()
                        .options(OpenAiChatOptions.builder().apiKey(loadApiKey()).build())
                        .build();

        var properties = provider == LlmProvider.OLLAMA
                ? new LlmProperties(provider, model, 2_000, 0.0, null,
                        new LlmProperties.Ollama(8_192, "10m", false))
                : new LlmProperties(provider, model, 4_000, 0.0,
                        new LlmProperties.OpenAi(model.startsWith("gpt-5") ? "low" : null), null);

        return new SourceDigestService(
                ChatClient.builder(chatModel).build(),
                promptRepository,
                provider == LlmProvider.OLLAMA
                        ? new OllamaChatOptionsFactory(properties)
                        : new OpenAiChatOptionsFactory(properties),
                new LlmUsageLogger(),
                new DigestContextTrimmer(new KnowledgeDigestProperties(40_000, 800))
        );
    }

    private String loadApiKey() {
        String fromEnv = System.getenv("OPENAI_API_KEY");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }

        Path envPath = Path.of("src/test/.env.test").toAbsolutePath().normalize();
        if (!Files.isRegularFile(envPath)) {
            envPath = Path.of("backend/src/test/.env.test").toAbsolutePath().normalize();
        }

        return Dotenv.configure()
                .directory(envPath.getParent().toString())
                .filename(envPath.getFileName().toString())
                .ignoreIfMissing()
                .load()
                .get("OPENAI_API_KEY", "");
    }
}
