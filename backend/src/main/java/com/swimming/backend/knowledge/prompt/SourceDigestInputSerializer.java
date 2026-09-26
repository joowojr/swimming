package com.swimming.backend.knowledge.prompt;

import com.swimming.backend.knowledge.dto.out.SourceDigestInput;

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

        xml.append("</source-digest-input>");

        return xml.toString();
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
