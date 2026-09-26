package com.swimming.backend.knowledge.prompt;

import com.swimming.backend.knowledge.dto.out.SourceDigestInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SourceDigestInputSerializerTest {

    @Test
    @DisplayName("제목·URL·본문을 XML로 감싼다")
    void serializesDocument() {
        String xml = SourceDigestInputSerializer.serialize(new SourceDigestInput(
                "Spring AI MCP Reference",
                "https://docs.spring.io/spring-ai/reference/api/mcp.html",
                "MCP Server를 구성하는 방법을 설명한다.",
                List.of()
        ));

        assertThat(xml)
                .startsWith("<source-digest-input>")
                .endsWith("</source-digest-input>")
                .contains("<title>Spring AI MCP Reference</title>")
                .contains("<url>https://docs.spring.io/spring-ai/reference/api/mcp.html</url>")
                .contains("<content><![CDATA[MCP Server를 구성하는 방법을 설명한다.]]></content>");
    }

    @Test
    @DisplayName("제목의 XML 특수문자를 이스케이프한다")
    void escapesTitle() {
        String xml = SourceDigestInputSerializer.serialize(new SourceDigestInput(
                "<script> & \"quoted\"",
                "https://example.com",
                "본문",
                List.of()
        ));

        assertThat(xml).contains("<title>&lt;script&gt; &amp; &quot;quoted&quot;</title>");
    }

    @Test
    @DisplayName("본문에 CDATA 종료 문자열이 있어도 섹션이 깨지지 않는다")
    void escapesCdataTerminatorInContent() {
        String xml = SourceDigestInputSerializer.serialize(new SourceDigestInput(
                "제목",
                "https://example.com",
                "코드에 ]]> 가 들어 있다",
                List.of()
        ));

        assertThat(xml).contains("<content><![CDATA[코드에 ]]]]><![CDATA[> 가 들어 있다]]></content>");
        assertThat(xml).endsWith("</source-digest-input>");
    }

    @Test
    @DisplayName("값이 없으면 빈 문자열로 채운다")
    void handlesNullValues() {
        String xml = SourceDigestInputSerializer.serialize(new SourceDigestInput(null, null, null, null));

        assertThat(xml).isEqualTo(
                "<source-digest-input><title></title><url></url>"
                        + "<content><![CDATA[]]></content></source-digest-input>"
        );
    }

    @Test
    @DisplayName("이미 만든 Topic 이름은 소화 입력에 포함하지 않는다")
    void doesNotSerializeExistingTopics() {
        String xml = SourceDigestInputSerializer.serialize(new SourceDigestInput(
                "제목",
                "https://example.com",
                "본문",
                List.of("MCP 서버 구현하기", "RAG 파이프라인 구현하기")
        ));

        assertThat(xml).doesNotContain("existing-topics");
        assertThat(xml).doesNotContain("MCP 서버 구현하기", "RAG 파이프라인 구현하기");
    }
}
