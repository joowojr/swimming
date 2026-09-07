package com.swimming.backend.knowledge.service.crawl;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.html2md.converter.LinkConversion;
import com.vladsch.flexmark.util.data.DataHolder;
import com.vladsch.flexmark.util.data.MutableDataSet;
import net.dankito.readability4j.Article;
import net.dankito.readability4j.Readability4J;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * HTML을 본문만 남긴 마크다운으로 바꾼다.
 *
 * <p>Readability로 내비게이션·사이드바·푸터를 걷어낸다. 이 단계를 건너뛰면 마크다운이
 * 원본 HTML보다 커지고 본문이 부속 링크에 묻힌다.
 *
 * <p>다만 Readability는 문서 구조에 따라 본문 컨테이너를 잘못 골라 내용을 크게 잃는다.
 * 실제로 Spring Data JPA 레퍼런스에서 본문 46,000자 중 6,000자만 남기고 헤딩을 전부
 * 놓쳤다. 그래서 semantic 컨테이너({@code article}/{@code main})를 함께 뽑아 두고,
 * Readability 결과가 그보다 현저히 적을 때만 그쪽으로 바꾼다.
 *
 * <p>라이브러리 의존은 이 클래스 안에만 둔다. 변환기를 갈아끼울 때 여기만 바뀐다.
 */
@Component
public class HtmlToMarkdownConverter {

    private static final DataHolder OPTIONS = new MutableDataSet()
            .set(FlexmarkHtmlConverter.SKIP_ATTRIBUTES, true)
            .set(FlexmarkHtmlConverter.OUTPUT_ATTRIBUTES_ID, false)
            .set(FlexmarkHtmlConverter.SETEXT_HEADINGS, false)
            .set(FlexmarkHtmlConverter.EXT_INLINE_LINK, LinkConversion.TEXT)
            .set(FlexmarkHtmlConverter.EXT_INLINE_IMAGE, LinkConversion.NONE)
            .toImmutable();

    /** 문서에 하나만 있을 때 본문으로 인정하는 컨테이너. 여럿이면 목록 페이지로 본다. */
    private static final String[] SEMANTIC_SELECTORS = {"article", "main", "[role=main]"};

    /** semantic 컨테이너 안에 남아 있는 부속 요소. */
    private static final String CHROME_SELECTOR = String.join(", ",
            "nav", "menu", "[role=navigation]", "aside", "footer", "header",
            "script", "style", "noscript", "form", ".toc", ".nav", ".navbar", ".menu",
            ".breadcrumbs", ".edit-this-page", ".sidebar");

    /**
     * Readability 결과가 semantic 컨테이너보다 이 배수 이상 적으면 추출에 실패한 것으로 본다.
     *
     * <p>측정한 8개 문서에서 정상 추출의 최대 비율이 1.63, 실패 사례가 3.46과 7.16이었다.
     * 2.0은 그 사이에서 양쪽과 충분히 떨어져 있다.
     */
    private static final double SEMANTIC_PREFERENCE_RATIO = 2.0;

    public Result convert(String url, Document document) {
        Article article = new Readability4J(url, document.html()).parse();

        String readabilityHtml = article.getContent();
        String bodyHtml = chooseBody(document, readabilityHtml);

        String markdown = FlexmarkHtmlConverter.builder(OPTIONS)
                .build()
                .convert(bodyHtml)
                .strip();

        String title = StringUtils.hasText(article.getTitle())
                ? article.getTitle()
                : document.title();

        return new Result(title, article.getByline(), markdown);
    }

    String chooseBody(Document document, String readabilityHtml) {
        Element semantic = semanticContent(document);
        String selected;

        if (semantic == null) {
            selected = StringUtils.hasText(readabilityHtml)
                    ? readabilityHtml
                    : document.body().html();
        } else if (!StringUtils.hasText(readabilityHtml)) {
            selected = semantic.html();
        } else {
            int readabilityLength = Jsoup.parse(readabilityHtml).text().length();
            int semanticLength = semantic.text().length();

            boolean readabilityLostContent = readabilityLength == 0
                    || semanticLength > readabilityLength * SEMANTIC_PREFERENCE_RATIO;

            selected = readabilityLostContent ? semantic.html() : readabilityHtml;
        }

        return removeChrome(selected);
    }

    /** 선택한 본문에서 메뉴와 내비게이션 등 읽을거리 밖의 요소를 제거한다. */
    private String removeChrome(String html) {
        Document fragment = Jsoup.parseBodyFragment(html);
        fragment.select(CHROME_SELECTOR).remove();
        return fragment.body().html();
    }

    private Element semanticContent(Document document) {
        for (String selector : SEMANTIC_SELECTORS) {
            Elements matched = document.select(selector);

            if (matched.size() == 1) {
                Element content = matched.first().clone();
                content.select(CHROME_SELECTOR).remove();
                return content;
            }
        }

        return null;
    }

    public record Result(String title, String byline, String markdown) {

        /** 링크 URL을 뺀 실제 읽을거리의 길이. 로그인 벽처럼 링크만 남은 페이지를 가려낸다. */
        public int meaningfulLength() {
            return meaningfulTextOf(markdown).length();
        }
    }

    /**
     * 마크다운에서 서식 기호를 걷어내고 실제 문장만 남긴다.
     *
     * <p>마크다운 전체 길이는 본문 유무를 판단하는 기준으로 쓸 수 없다. 헤딩 기호와 목록
     * 기호, 표 구분선이 분량을 부풀린다. 로그인 안내와 푸터 링크만 있는 X 게시물 페이지가
     * 그렇게 걸러지지 않아 사이트 메뉴가 본문으로 저장됐다.
     *
     * <p>링크 주소는 변환 단계에서 이미 빠지지만, 예전 데이터나 직접 만든 마크다운이
     * 들어올 수 있어 여기서도 한 번 더 걷어낸다.
     */
    static String meaningfulTextOf(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return "";
        }

        return markdown
                .replaceAll("!?\\[([^\\]]*)\\]\\([^)]*\\)", "$1")
                .replaceAll("[#*|>`_\\-\\s]+", " ")
                .strip();
    }
}
