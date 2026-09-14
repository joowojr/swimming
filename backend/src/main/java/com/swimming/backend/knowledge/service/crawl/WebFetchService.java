package com.swimming.backend.knowledge.service.crawl;

import com.swimming.backend.knowledge.config.WebFetchProperties;
import com.swimming.backend.knowledge.dto.out.FetchedDocument;
import com.swimming.backend.knowledge.dto.out.SourceFetchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * URL 하나를 받아 본문을 마크다운으로 만들어 돌려준다.
 *
 * <p>실패는 예외가 아니라 결과로 표시한다. 한 URL의 실패가 나머지를 막지 않아야 하기
 * 때문이며, 여러 URL을 묶어 처리하는 일은 {@link SourceFetchDispatcher}가 맡는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebFetchService {

    private static final int HTTP_FORBIDDEN = 403;

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private static final Set<String> HTML_CONTENT_TYPES = Set.of(
            "text/html",
            "application/xhtml+xml"
    );

    /** 메타데이터로 대체할 때 요구하는 최소 설명 길이. 이보다 짧으면 제목의 반복일 뿐이다. */
    private static final int MIN_METADATA_LENGTH = 40;

    /** 원본을 가리키지 않고 유입 경로만 기록하는 파라미터. canonical URL에서 뺀다. */
    private static final Set<String> TRACKING_PARAMETERS = Set.of(
            "fbclid", "gclid", "msclkid", "igshid", "mc_cid", "mc_eid", "ref", "ref_src"
    );

    private final WebFetchProperties properties;
    private final HtmlToMarkdownConverter markdownConverter;

    /** 렌더링 폴백은 꺼둘 수 있다. 꺼져 있으면 빈이 없다. */
    private final Optional<LambdaPageRendererClient> pageRenderer;

    public SourceFetchResult fetch(String url) {
        log.debug(
                "[source-fetch] started url={} rendererAvailable={} renderThreshold={}",
                url, pageRenderer.isPresent(), properties.render().minContentLength()
        );

        URI uri;
        try {
            uri = URI.create(url.strip());
        } catch (IllegalArgumentException exception) {
            log.debug("[source-fetch] rejected url={} reason=invalid-url", url);
            return SourceFetchResult.failure(
                    url,
                    SourceFetchResult.Failure.INVALID_URL,
                    exception.getMessage()
            );
        }

        SourceFetchResult.Failure rejected = reject(uri);
        if (rejected != null) {
            log.debug("[source-fetch] rejected url={} reason={}", url, rejected);
            return SourceFetchResult.failure(url, rejected, uri.getHost());
        }

        try {
            Connection.Response response = Jsoup.connect(uri.toString())
                    .userAgent(properties.userAgent())
                    .timeout((int) properties.timeout().toMillis())
                    .maxBodySize(properties.maxBodyBytes())
                    .followRedirects(true)
                    .ignoreContentType(false)
                    .execute();

            log.debug(
                    "[source-fetch] http response requestedUrl={} finalUrl={} status={} contentType={}",
                    url, response.url(), response.statusCode(), response.contentType()
            );

            if (!isHtml(response.contentType())) {
                log.debug(
                        "[source-fetch] rejected url={} reason=unsupported-content-type contentType={}",
                        url, response.contentType()
                );
                return SourceFetchResult.failure(
                        url,
                        SourceFetchResult.Failure.UNSUPPORTED_CONTENT_TYPE,
                        response.contentType()
                );
            }

            return toDocument(url, response.parse());

        } catch (HttpStatusException exception) {
            return recoverForbidden(url, exception);
        } catch (UnsupportedMimeTypeException exception) {
            return SourceFetchResult.failure(
                    url,
                    SourceFetchResult.Failure.UNSUPPORTED_CONTENT_TYPE,
                    exception.getMimeType()
            );
        } catch (SocketTimeoutException exception) {
            return SourceFetchResult.failure(
                    url,
                    SourceFetchResult.Failure.TIMEOUT,
                    properties.timeout().toString()
            );
        } catch (IOException exception) {
            log.info("[source-fetch] failed url={} reason={}", url, exception.toString());
            return SourceFetchResult.failure(
                    url,
                    SourceFetchResult.Failure.UNKNOWN,
                    exception.getMessage()
            );
        }
    }

    /**
     * 일반 HTTP 클라이언트만 차단하는 사이트는 브라우저로 한 번 더 가져온다.
     *
     * <p>403 이외의 상태는 없는 문서나 인증이 필요한 문서일 수 있으므로 그대로 실패시킨다.
     * 렌더러도 실패하거나 유효한 본문을 만들지 못하면 최초 403 결과를 보존한다.
     */
    private SourceFetchResult recoverForbidden(
            String requestedUrl,
            HttpStatusException exception
    ) {
        int statusCode = exception.getStatusCode();
        SourceFetchResult failed = SourceFetchResult.failure(
                requestedUrl,
                SourceFetchResult.Failure.HTTP_ERROR,
                String.valueOf(statusCode)
        );

        if (statusCode != HTTP_FORBIDDEN || pageRenderer.isEmpty()) {
            log.debug(
                    "[source-fetch] renderer skipped url={} reason={} status={}",
                    requestedUrl,
                    statusCode != HTTP_FORBIDDEN ? "status-not-supported" : "renderer-disabled",
                    statusCode
            );
            return failed;
        }

        String blockedUrl = StringUtils.hasText(exception.getUrl())
                ? exception.getUrl()
                : requestedUrl;

        log.debug(
                "[source-fetch] renderer requested url={} reason=http-forbidden",
                blockedUrl
        );

        return pageRenderer.get().render(blockedUrl)
                .map(html -> toDocument(
                        requestedUrl,
                        Jsoup.parse(html, blockedUrl),
                        false
                ))
                .filter(SourceFetchResult::isSuccess)
                .map(recovered -> {
                    log.info(
                            "[source-fetch] recovered http error url={} status={} via=renderer",
                            requestedUrl, statusCode
                    );
                    return recovered;
                })
                .orElse(failed);
    }

    /**
     * jsoup은 JavaScript를 실행하지 않는다. 마크다운이 거의 비어 나오면 본문을 스크립트가
     * 그리는 페이지로 보고 헤드리스 브라우저로 다시 받는다.
     *
     * <p>렌더링에 실패하거나 더 짧아지면 원래 결과를 그대로 쓴다.
     */
    private HtmlToMarkdownConverter.Result renderIfEmpty(
            String url,
            HtmlToMarkdownConverter.Result converted
    ) {
        int length = converted.meaningfulLength();
        log.info(
                "[source-fetch] render-check url={} meaningfulLength={} threshold={} rendererAvailable={}",
                url,
                length,
                properties.render().minContentLength(),
                pageRenderer.isPresent()
        );

        if (length >= properties.render().minContentLength()) {
            log.debug(
                    "[source-fetch] renderer skipped url={} reason=content-sufficient meaningfulLength={} threshold={}",
                    url, length, properties.render().minContentLength()
            );
            return converted;
        }

        if (pageRenderer.isEmpty()) {
            log.debug(
                    "[source-fetch] renderer skipped url={} reason=renderer-disabled meaningfulLength={} threshold={}",
                    url, length, properties.render().minContentLength()
            );
            return converted;
        }

        log.debug(
                "[source-fetch] renderer requested url={} reason=content-short meaningfulLength={} threshold={}",
                url, length, properties.render().minContentLength()
        );

        Optional<String> renderedHtml = pageRenderer.get().render(url);
        if (renderedHtml.isEmpty()) {
            log.debug("[source-fetch] renderer unavailable url={}", url);
            return converted;
        }

        HtmlToMarkdownConverter.Result rendered = markdownConverter.convert(
                url, Jsoup.parse(renderedHtml.get(), url)
        );
        int renderedLength = rendered.meaningfulLength();
        if (renderedLength <= length) {
            log.debug(
                    "[source-fetch] renderer result ignored url={} reason=not-improved meaningfulLength={} renderedMeaningfulLength={}",
                    url, length, renderedLength
            );
            return converted;
        }

        log.info(
                "[source-fetch] rendered url={} text {} -> {}",
                url, length, renderedLength
        );
        return rendered;
    }


    /**
     * 본문 추출이 사실상 실패했을 때 문서가 스스로 밝힌 설명으로 대체한다.
     *
     * <p>로그인 벽이나 SNS 게시물처럼 DOM에 본문이 없는 페이지가 있다. 그대로 두면 "Log in
     * or sign up", "Trending now" 같은 사이트 메뉴가 본문으로 저장된다. 이런 페이지도
     * {@code og:description}에는 실제 글이 들어 있는 경우가 많다.
     *
     * <p>제목도 함께 바꾼다. X는 {@code <title>}에 게시물 본문을 통째로 넣지만
     * {@code og:title}은 작성자만 담아 제목으로 쓰기에 알맞다.
     *
     * <p>설명이 기존 본문보다 짧아도 바꾼다. 여기까지 왔다는 것은 기존 본문이 이미 사이트
     * 메뉴라는 뜻이라, 길이로 비교하면 메뉴가 이긴다.
     */
    private HtmlToMarkdownConverter.Result fallBackToMetadata(
            String url,
            Document document,
            HtmlToMarkdownConverter.Result converted
    ) {
        if (converted.meaningfulLength() >= properties.minMeaningfulLength()) {
            log.debug(
                    "[source-fetch] metadata skipped url={} reason=content-sufficient meaningfulLength={} threshold={}",
                    url, converted.meaningfulLength(), properties.minMeaningfulLength()
            );
            return converted;
        }

        String description = metaContent(document, "meta[property=og:description]");
        if (description == null) {
            description = metaContent(document, "meta[name=description]");
        }
        if (description == null || description.length() < MIN_METADATA_LENGTH) {
            log.debug(
                    "[source-fetch] metadata unavailable url={} meaningfulLength={} descriptionLength={}",
                    url,
                    converted.meaningfulLength(),
                    description == null ? 0 : description.length()
            );
            return converted;
        }

        String title = metaContent(document, "meta[property=og:title]");

        log.info(
                "[source-fetch] used metadata url={} text {} -> {}",
                url, converted.meaningfulLength(), description.length()
        );

        return new HtmlToMarkdownConverter.Result(
                title != null ? title : converted.title(),
                converted.byline(),
                description
        );
    }

    /** 네트워크 없이 파싱 단계만 확인할 수 있도록 열어 둔다. */
    SourceFetchResult fetchFrom(Document document) {
        return toDocument(document.location(), document);
    }

    private SourceFetchResult toDocument(String requestedUrl, Document document) {
        return toDocument(requestedUrl, document, true);
    }

    private SourceFetchResult toDocument(
            String requestedUrl,
            Document document,
            boolean allowRenderFallback
    ) {
        String finalUrl = document.location();
        HtmlToMarkdownConverter.Result converted = markdownConverter.convert(finalUrl, document);
        log.info(
                "[source-fetch] converted url={} convertedLength={} meaningfulLength={} renderAllowed={}",
                finalUrl,
                converted.markdown().length(),
                converted.meaningfulLength(),
                allowRenderFallback
        );
        if (allowRenderFallback) {
            converted = renderIfEmpty(finalUrl, converted);
        }
        converted = fallBackToMetadata(finalUrl, document, converted);

        if (!StringUtils.hasText(converted.markdown())) {
            log.debug(
                    "[source-fetch] failed requestedUrl={} finalUrl={} reason=empty-content meaningfulLength={}",
                    requestedUrl, finalUrl, converted.meaningfulLength()
            );
            return SourceFetchResult.failure(
                    requestedUrl,
                    SourceFetchResult.Failure.SOURCE_EMPTY_CONTENT,
                    null
            );
        }

        Truncation truncation = truncate(converted.markdown());
        log.debug(
                "[source-fetch] finished requestedUrl={} finalUrl={} markdownLength={} meaningfulLength={} truncated={}",
                requestedUrl,
                finalUrl,
                converted.markdown().length(),
                converted.meaningfulLength(),
                truncation.truncated()
        );

        return SourceFetchResult.success(requestedUrl, new FetchedDocument(
                finalUrl,
                canonicalUrl(document, requestedUrl, finalUrl),
                converted.title(),
                author(document, converted.byline()),
                publishedAt(document),
                metaContent(document, "meta[property=og:type]"),
                truncation.text(),
                truncation.truncated()
        ));
    }

    /**
     * 서버가 대신 요청해 주는 구조라, 사용자가 준 URL로 내부망을 들여다볼 수 있으면 안 된다.
     */
    private SourceFetchResult.Failure reject(URI uri) {
        String scheme = uri.getScheme();

        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            return SourceFetchResult.Failure.INVALID_URL;
        }
        if (!StringUtils.hasText(uri.getHost())) {
            return SourceFetchResult.Failure.INVALID_URL;
        }

        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    return SourceFetchResult.Failure.BLOCKED_ADDRESS;
                }
            }
        } catch (UnknownHostException exception) {
            return SourceFetchResult.Failure.INVALID_URL;
        }

        return null;
    }

    private boolean isHtml(String contentType) {
        if (contentType == null) {
            return false;
        }

        String mimeType = contentType.split(";")[0].strip().toLowerCase(Locale.ROOT);
        return HTML_CONTENT_TYPES.contains(mimeType);
    }

    /**
     * 문서가 스스로 밝힌 정본 주소를 우선하고, 없으면 추적 파라미터와 fragment를 걷어낸다.
     */
    String canonicalUrl(Document document, String requestedUrl, String finalUrl) {
        String declared = document.select("link[rel=canonical]").attr("abs:href");
        if (!StringUtils.hasText(declared)) {
            declared = document.select("meta[property=og:url]").attr("abs:content");
        }

        if (StringUtils.hasText(declared)) {
            String normalizedDeclared = normalizeCanonicalUrl(declared);

            // Notion 공개 페이지가 서비스 루트만 canonical로 선언하면 서로 다른 문서가
            // 하나로 합쳐진다. 확인된 Notion 루트 값에만 예외를 두고, 다른 사이트가 선언한
            // canonical의 의미는 바꾸지 않는다.
            if (isNotionRootCanonical(normalizedDeclared)) {
                String normalizedFinal = normalizeCanonicalUrl(finalUrl);
                if (hasDocumentIdentity(normalizedFinal)) {
                    return normalizedFinal;
                }

                String normalizedRequested = normalizeCanonicalUrl(requestedUrl);
                if (hasDocumentIdentity(normalizedRequested)) {
                    return normalizedRequested;
                }
            }

            return normalizedDeclared;
        }

        return normalizeCanonicalUrl(finalUrl);
    }

    private boolean isNotionRootCanonical(String url) {
        try {
            URI uri = URI.create(url);
            return "app.notion.com".equalsIgnoreCase(uri.getHost())
                    && !hasDocumentIdentity(url);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean hasDocumentIdentity(String url) {
        try {
            URI uri = URI.create(url);
            return (StringUtils.hasText(uri.getPath()) && !"/".equals(uri.getPath()))
                    || StringUtils.hasText(uri.getQuery());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String normalizeCanonicalUrl(String base) {
        try {
            URI uri = URI.create(base);
            String query = stripTrackingParameters(uri.getQuery());

            return new URI(
                    uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT),
                    uri.getUserInfo(),
                    uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT),
                    uri.getPort(),
                    uri.getPath(),
                    query,
                    null
            ).toString();
        } catch (Exception exception) {
            return base;
        }
    }

    private String stripTrackingParameters(String query) {
        if (!StringUtils.hasText(query)) {
            return null;
        }

        List<String> kept = new ArrayList<>();
        for (String parameter : query.split("&")) {
            int separator = parameter.indexOf('=');
            String key = (separator >= 0 ? parameter.substring(0, separator) : parameter)
                    .toLowerCase(Locale.ROOT);

            if (key.startsWith("utm_") || TRACKING_PARAMETERS.contains(key)) {
                continue;
            }
            kept.add(parameter);
        }

        return kept.isEmpty() ? null : String.join("&", kept);
    }

    private String author(Document document, String byline) {
        if (StringUtils.hasText(byline)) {
            return byline.strip();
        }

        String author = metaContent(document, "meta[name=author]");
        return author != null ? author : metaContent(document, "meta[property=article:author]");
    }

    private Instant publishedAt(Document document) {
        String[] selectors = {
                "meta[property=article:published_time]",
                "meta[itemprop=datePublished]",
                "meta[name=date]"
        };

        for (String selector : selectors) {
            Instant parsed = parseInstant(metaContent(document, selector));
            if (parsed != null) {
                return parsed;
            }
        }

        return parseInstant(document.select("time[datetime]").attr("datetime"));
    }

    private Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            return OffsetDateTime.parse(value.strip()).toInstant();
        } catch (DateTimeParseException ignored) {
            // 날짜만 적힌 문서가 흔하다
        }

        try {
            return LocalDate.parse(value.strip()).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String metaContent(Document document, String selector) {
        String content = document.select(selector).attr("content").strip();
        return content.isEmpty() ? null : content;
    }

    /**
     * 잘릴 때는 줄 경계에서 끊어 마크다운 구조가 문장 중간에 깨지지 않게 한다.
     */
    Truncation truncate(String markdown) {
        int limit = properties.maxContentLength();

        if (markdown.length() <= limit) {
            return new Truncation(markdown, false);
        }

        String cut = markdown.substring(0, limit);
        int lastBreak = cut.lastIndexOf('\n');

        if (lastBreak > limit * 0.8) {
            cut = cut.substring(0, lastBreak);
        }

        return new Truncation(cut.strip(), true);
    }

    record Truncation(String text, boolean truncated) {
    }
}
