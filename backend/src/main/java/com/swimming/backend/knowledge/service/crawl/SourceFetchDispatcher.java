package com.swimming.backend.knowledge.service.crawl;

import com.swimming.backend.knowledge.config.WebFetchProperties;
import com.swimming.backend.knowledge.dto.out.SourceFetchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * URL을 보고 어떤 수집기가 맡을지 정한다.
 *
 * <p>{@link SourceFetcher} 구현체를 순서대로 물어보고 처음 맡겠다고 한 곳에 넘긴다.
 * 아무도 맡지 않으면 일반 웹 수집으로 보낸다. 수집 방식이 하나 늘 때 하는 일은 구현체를
 * 추가하는 것뿐이고 이 클래스는 그대로 둔다.
 *
 * <p>전용 수집기는 설정이 갖춰졌을 때만 빈으로 올라온다. 유튜브 API 키가 없으면
 * 유튜브 링크도 일반 웹 수집으로 떨어져, 설정 없이도 지금 동작이 그대로 유지된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SourceFetchDispatcher {

    /** 맡겠다고 나선 수집기가 없을 때 쓰는 기본 경로. */
    private final WebFetchService webFetchService;

    /** 주입 순서가 곧 우선순위다. {@code @Order}로 정한다. */
    private final List<SourceFetcher> fetchers;

    private final WebFetchProperties properties;

    /**
     * 입력 순서를 유지하고, 같은 URL이 여러 번 오면 한 번만 요청한다.
     */
    public List<SourceFetchResult> fetchAll(List<String> urls) {
        List<String> targets = new ArrayList<>(new LinkedHashSet<>(urls));

        Semaphore permits = new Semaphore(properties.concurrency());
        List<SourceFetchResult> results = new ArrayList<>(targets.size());

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<SourceFetchResult>> futures = targets.stream()
                    .map(url -> executor.submit(() -> {
                        permits.acquire();
                        try {
                            return fetch(url);
                        } finally {
                            permits.release();
                        }
                    }))
                    .toList();

            for (int i = 0; i < futures.size(); i++) {
                results.add(resultOf(targets.get(i), futures.get(i)));
            }
        }

        return results;
    }

    /**
     * 파싱하지 못한 URL도 일반 웹 수집으로 넘긴다. 거기서 {@code INVALID_URL}로 판정하므로
     * 형식 검증을 두 곳에 두지 않는다.
     */
    public SourceFetchResult fetch(String url) {
        Optional<URI> uri = parse(url);
        if (uri.isEmpty()) {
            return webFetchService.fetch(url);
        }

        return fetcherFor(uri.get())
                .map(fetcher -> fetcher.fetch(url, uri.get()))
                .orElseGet(() -> webFetchService.fetch(url));
    }

    private Optional<SourceFetcher> fetcherFor(URI uri) {
        return fetchers.stream()
                .filter(fetcher -> fetcher.supports(uri))
                .findFirst();
    }

    private Optional<URI> parse(String url) {
        if (url == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(URI.create(url.strip()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private SourceFetchResult resultOf(String url, Future<SourceFetchResult> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return SourceFetchResult.failure(
                    url,
                    SourceFetchResult.Failure.UNKNOWN,
                    "interrupted"
            );
        } catch (Exception exception) {
            return SourceFetchResult.failure(
                    url,
                    SourceFetchResult.Failure.UNKNOWN,
                    exception.getMessage()
            );
        }
    }
}
