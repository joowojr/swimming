package com.swimming.backend.knowledge.service.crawl;

import com.swimming.backend.knowledge.service.crawl.HtmlToMarkdownConverter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlToMarkdownConverterTest {

    private static final String URL = "https://example.com/article";

    private final HtmlToMarkdownConverter converter = new HtmlToMarkdownConverter();

    private Document parse(String html) {
        return Jsoup.parse(html, URL);
    }

    private String articleHtml(String body) {
        return """
                <html><head><title>문서 제목</title></head>
                <body>
                  <nav><ul><li><a href="/home">홈</a></li><li><a href="/about">소개</a></li></ul></nav>
                  <article>%s</article>
                  <footer><p>© 2026 Example. 모든 권리 보유.</p></footer>
                </body></html>
                """.formatted(body);
    }

    @Test
    @DisplayName("내비게이션과 푸터를 걷어내고 본문만 남긴다")
    void dropsBoilerplate() {
        String longBody = """
                <h1>MCP 서버 구현하기</h1>
                <p>Model Context Protocol은 LLM과 애플리케이션을 잇는 개방형 표준이다.
                   서버는 Tool을 노출하고 클라이언트는 그것을 호출한다. 이 문서는 Spring AI에서
                   MCP 서버를 구성하는 절차를 처음부터 끝까지 설명한다.</p>
                <p>먼저 의존성을 추가하고, Tool을 정의한 뒤, 클라이언트를 연결한다.
                   각 단계마다 확인할 지점이 있으며 순서를 지키는 편이 문제를 줄인다.</p>
                """;

        var result = converter.convert(URL, parse(articleHtml(longBody)));

        assertThat(result.markdown()).contains("MCP 서버 구현하기");
        assertThat(result.markdown()).contains("Model Context Protocol");
        assertThat(result.markdown()).doesNotContain("모든 권리 보유");
        assertThat(result.markdown()).doesNotContain("소개");
    }

    @Test
    @DisplayName("헤딩·목록·코드·표 구조를 마크다운으로 옮긴다")
    void keepsStructure() {
        String body = """
                <h1>제목</h1>
                <p>이 문서는 구조가 보존되는지 확인하기 위한 본문이며 충분한 길이를 갖도록 문장을 늘린다.
                   Readability는 지나치게 짧은 본문을 버리는 경향이 있어 최소 분량이 필요하다.</p>
                <h2>목록</h2>
                <ul><li>첫째 항목</li><li>둘째 항목</li></ul>
                <pre><code>System.out.println("hello");</code></pre>
                <table><tr><th>이름</th><th>값</th></tr><tr><td>a</td><td>1</td></tr></table>
                """;

        String markdown = converter.convert(URL, parse(articleHtml(body))).markdown();

        assertThat(markdown).contains("# 제목");
        assertThat(markdown).contains("## 목록");
        assertThat(markdown).contains("* 첫째 항목");
        assertThat(markdown).contains("System.out.println");
        assertThat(markdown).contains("| 이름");
    }

    @Test
    @DisplayName("Kramdown 속성 주석을 남기지 않는다")
    void omitsAttributeNoise() {
        String body = """
                <h1 id="heading-id">제목</h1>
                <p id="para-id" class="lead">본문은 Readability가 버리지 않을 만큼 길어야 하므로
                   여러 문장을 이어 붙여 충분한 분량을 확보한다. 속성이 마크다운에 새어 나오는지 본다.</p>
                """;

        String markdown = converter.convert(URL, parse(articleHtml(body))).markdown();

        assertThat(markdown).doesNotContain("{#heading-id}");
        assertThat(markdown).doesNotContain("{#para-id}");
    }

    @Test
    @DisplayName("본문 추출이 실패하면 문서 전체를 옮겨 내용을 잃지 않는다")
    void fallsBackToWholeBody() {
        Document document = parse("<html><head><title>짧은 글</title></head><body><p>짧다</p></body></html>");

        var result = converter.convert(URL, document);

        assertThat(result.markdown()).contains("짧다");
        assertThat(result.title()).isEqualTo("짧은 글");
    }

    @Test
    @DisplayName("Readability가 본문을 크게 놓치면 semantic 컨테이너를 대신 쓴다")
    void prefersSemanticWhenReadabilityUnderExtracts() {
        Document document = parse("""
                <html><body>
                  <main>
                    <h1>큰 제목</h1>
                    <h2>절 하나</h2><p>%s</p>
                    <h2>절 둘</h2><p>%s</p>
                  </main>
                </body></html>
                """.formatted("본문".repeat(400), "내용".repeat(400)));

        String chosen = converter.chooseBody(document, "<p>일부만 뽑힌 짧은 조각</p>");

        assertThat(chosen).contains("큰 제목", "절 하나", "절 둘");
    }

    @Test
    @DisplayName("Readability 결과가 충분하면 그대로 쓴다")
    void keepsReadabilityWhenComparable() {
        Document document = parse("""
                <html><body><main><p>%s</p></main></body></html>
                """.formatted("본문".repeat(300)));

        String readability = "<p>" + "본문".repeat(290) + "</p>";

        assertThat(converter.chooseBody(document, readability)).isEqualTo(readability);
    }

    @Test
    @DisplayName("article이 여러 개면 목록 페이지로 보고 semantic 컨테이너로 쓰지 않는다")
    void ignoresMultipleArticles() {
        Document document = parse("""
                <html><body>
                  <article><h2>글 1</h2><p>%s</p></article>
                  <article><h2>글 2</h2><p>%s</p></article>
                </body></html>
                """.formatted("가".repeat(500), "나".repeat(500)));

        String readability = "<p>짧은 조각</p>";

        assertThat(converter.chooseBody(document, readability)).isEqualTo(readability);
    }

    @Test
    @DisplayName("semantic 컨테이너 안에 남은 내비게이션과 푸터를 걷어낸다")
    void stripsChromeInsideSemanticContainer() {
        Document document = parse("""
                <html><body>
                  <main>
                    <nav><a href="/x">사이드 메뉴</a></nav>
                    <h1>제목</h1><p>%s</p>
                    <footer>바닥글 저작권</footer>
                  </main>
                </body></html>
                """.formatted("본문".repeat(400)));

        String chosen = converter.chooseBody(document, "<p>짧은 조각</p>");

        assertThat(chosen).contains("제목");
        assertThat(chosen).doesNotContain("사이드 메뉴");
        assertThat(chosen).doesNotContain("바닥글 저작권");
    }

    @Test
    @DisplayName("링크는 글자만 남기고 주소를 버린다")
    void keepsLinkTextWithoutUrl() {
        String body = """
                <h1>제목</h1>
                <p>%s 자세한 내용은 <a href="https://example.com/very/long/path?utm_source=x">공식 문서</a>를 보세요.</p>
                """.formatted("본문이 충분히 길어야 Readability가 버리지 않는다. ".repeat(20));

        String markdown = converter.convert(URL, parse(articleHtml(body))).markdown();

        assertThat(markdown).contains("공식 문서");
        assertThat(markdown).doesNotContain("https://example.com");
        assertThat(markdown).doesNotContain("](");
    }

    @Test
    @DisplayName("이미지는 통째로 제거한다")
    void dropsImages() {
        String body = """
                <h1>제목</h1>
                <p>%s</p>
                <p><img src="https://cdn.example.com/diagram.png" alt=""></p>
                """.formatted("본문이 충분히 길어야 Readability가 버리지 않는다. ".repeat(20));

        String markdown = converter.convert(URL, parse(articleHtml(body))).markdown();

        assertThat(markdown).doesNotContain("cdn.example.com");
        assertThat(markdown).doesNotContain("![");
    }
}
