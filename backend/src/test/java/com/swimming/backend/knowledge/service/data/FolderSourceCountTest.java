package com.swimming.backend.knowledge.service.data;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.folder.domain.Folder;
import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.service.graph.SourceGraphWriter;
import com.swimming.backend.knowledge.usecase.SourceCollectUseCase;
import com.swimming.backend.knowledge.usecase.SourceDeleteUseCase;
import com.swimming.backend.knowledge.usecase.SourceDigestProcessor;
import com.swimming.backend.knowledge.service.crawl.SourceFetchDispatcher;
import com.swimming.backend.knowledge.dto.in.SourceCollectRequest;
import com.swimming.backend.knowledge.dto.out.FetchedDocument;
import com.swimming.backend.knowledge.dto.out.SourceFetchResult;
import com.swimming.backend.user.domain.User;
import com.swimming.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:folder-link-count;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never",
        "spring.ai.openai.api-key=test",
        "app.place.background.cdn-base-url=https://cdn.example.com"
})
class FolderSourceCountTest {
    @Autowired KnowledgeSourceService sourceService;
    @Autowired SourceGraphWriter graphWriter;
    @Autowired SourceCollectUseCase collectUseCase;
    @Autowired SourceDeleteUseCase deleteUseCase;
    @MockitoBean SourceFetchDispatcher fetchDispatcher;
    @MockitoBean SourceDigestProcessor digestProcessor;
    @Autowired FolderService folderService;
    @Autowired UserRepository userRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;

    private Long userId;
    private Long folderId;

    @BeforeEach
    void 준비() {
        String key = UUID.randomUUID().toString();
        userId = userRepository.saveAndFlush(User.builder()
                .email(key + "@example.com").googleSubject(key).nickname("테스트")
                .timezone("Asia/Seoul").build()).getId();
        folderId = folderService.create(Folder.create(userId, null, "링크", "설명", null)).getId();
        when(fetchDispatcher.fetchAll(anyList())).thenAnswer(invocation -> {
            List<String> urls = invocation.getArgument(0);
            return urls.stream().map(url -> SourceFetchResult.success(url,
                    new FetchedDocument(url, url, "문서", null, null, "article", "본문", false))).toList();
        });
    }

    private KnowledgeSource 저장(String url) {
        var response = collectUseCase.collect(userId, folderId, new SourceCollectRequest(List.of(url)));
        return sourceService.getOwned(response.items().getFirst().source().sourceId(), userId);
    }

    private void 개수_검증(long expected) {
        assertThat(folderService.getSourceCount(userId, folderId)).isEqualTo(expected);
        assertThat(folderService.getOne(userId, folderId).isHasSource()).isEqualTo(expected > 0);
        assertThat(jdbc.queryForObject("""
                select count(*) from knowledge_source s join knowledge_node n on n.id = s.node_id
                where s.folder_id = ? and n.is_deleted = false
                """, Long.class, folderId)).isEqualTo(expected);
    }

    @Test
    void 중복_저장과_소화_결과_저장은_개수를_늘리지_않는다() {
        KnowledgeSource saved = 저장("https://example.com/a");
        KnowledgeSource duplicate = 저장("https://example.com/a");
        assertThat(duplicate.getId()).isEqualTo(saved.getId());
        saved.applyExtractedDocument("문서", "본문", "article", null, null);
        sourceService.save(saved);
        개수_검증(1);
        deleteUseCase.delete(userId, saved.getId());
        개수_검증(0);
        assertThatThrownBy(() -> deleteUseCase.delete(userId, saved.getId())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> sourceService.save(saved)).isInstanceOf(BusinessException.class);
        개수_검증(0);
    }

    @Test
    void writer가_참여한_트랜잭션이_롤백되면_링크와_개수도_함께_롤백된다() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            graphWriter.saveSourceInTransaction(KnowledgeSource.create(
                    userId, folderId, "문서", "https://example.com/a", "https://example.com/a"));
            개수_검증(1);
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        개수_검증(0);
    }

    private void 동시에_저장(String firstUrl, String secondUrl) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Callable<KnowledgeSource>> calls = List.of(firstUrl, secondUrl).stream()
                    .<Callable<KnowledgeSource>>map(url -> () -> {
                        ready.countDown();
                        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                        return 저장(url);
                    }).toList();
            var first = executor.submit(calls.getFirst());
            var second = executor.submit(calls.getLast());
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void 서로_다른_링크를_동시에_저장해도_개수_증가가_유실되지_않는다() throws Exception {
        동시에_저장("https://example.com/a", "https://example.com/b");
        개수_검증(2);
    }

    @Test
    void 같은_링크를_동시에_저장하면_한_번만_생성하고_센다() throws Exception {
        동시에_저장("https://example.com/a", "https://example.com/a");
        개수_검증(1);
    }
}
