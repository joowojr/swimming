package com.swimming.backend.knowledge.prompt;

import com.swimming.backend.knowledge.dto.out.NodeResolutionInput;

import java.util.List;

/**
 * 판정 입력을 섹션 단위로 적는다.
 *
 * <p>항목마다 태그를 두르면 이름보다 태그가 길어지므로 번호 줄로 적는다. 번호는 응답이
 * 가리킬 index다. 섹션 이름은 출처가 아니라 역할을 말한다.
 */
public final class NodeResolutionInputSerializer {

    private NodeResolutionInputSerializer() {
    }

    public static String serialize(NodeResolutionInput input) {
        StringBuilder text = new StringBuilder();

        text.append("<summary>\n")
                .append(escapeXml(input.summary()))
                .append("\n</summary>");

        appendSection(text, "subjects-to-resolve", "C", input.candidates().stream()
                .map(NodeResolutionInput.Candidate::value)
                .toList());

        if (!input.existingSubjects().isEmpty()) {
            appendSection(text, "reusable-subjects", "R", input.existingSubjects().stream()
                    .map(NodeResolutionInput.ExistingSubject::value)
                    .toList());
        }

        return text.toString();
    }

    /** 두 목록의 번호를 접두어로 갈라 둔다. 응답이 어느 번호를 가리키는지 헷갈릴 자리를 없앤다. */
    private static void appendSection(
            StringBuilder text,
            String name,
            String prefix,
            List<String> values
    ) {
        text.append("\n<").append(name).append(">");
        for (int index = 0; index < values.size(); index++) {
            text.append("\n")
                    .append(prefix)
                    .append(index + 1)
                    .append(". ")
                    .append(escapeXml(singleLine(values.get(index))));
        }
        text.append("\n</").append(name).append(">");
    }

    /** 줄바꿈이 남아 있으면 값 하나가 여러 항목처럼 보인다. */
    private static String singleLine(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ");
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
}
