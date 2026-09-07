package com.swimming.backend.knowledge.prompt;

import com.swimming.backend.knowledge.dto.out.SourceDigestInput;

import java.util.List;

public final class SourceDigestInputSerializer {

    private SourceDigestInputSerializer() {
    }

    public static String serialize(SourceDigestInput input) {
        StringBuilder xml = new StringBuilder();

        xml.append("<source-digest-input>");

        xml.append("<title>")
                .append(escapeXml(input.title()))
                .append("</title>");

        xml.append("<url>")
                .append(escapeXml(input.url()))
                .append("</url>");

        xml.append("<content><![CDATA[")
                .append(escapeCdata(input.content()))
                .append("]]></content>");

        appendExistingTopics(xml, input.existingTopics());

        xml.append("</source-digest-input>");

        return xml.toString();
    }

    /** 참고할 것이 없으면 요소 자체를 넣지 않는다. 빈 목록은 모델에게 읽을 거리만 늘린다. */
    private static void appendExistingTopics(StringBuilder xml, List<String> existingTopics) {
        if (existingTopics.isEmpty()) {
            return;
        }

        xml.append("<existing-topics>");

        for (String topic : existingTopics) {
            xml.append("<topic>").append(escapeXml(topic)).append("</topic>");
        }

        xml.append("</existing-topics>");
    }

    private static String escapeXml(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String escapeCdata(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("]]>", "]]]]><![CDATA[>");
    }
}
