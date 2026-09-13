package com.swimming.backend.knowledge.prompt;

import com.swimming.backend.knowledge.dto.out.NodeResolutionInputV2;

/** 후보와 후보별 임베딩 검색 결과를 중첩 구조로 직렬화한다. */
public final class NodeResolutionInputV2Serializer {

    private NodeResolutionInputV2Serializer() {
    }

    public static String serialize(NodeResolutionInputV2 input) {
        StringBuilder text = new StringBuilder("<summary>\n")
                .append(escape(input.summary()))
                .append("\n</summary>\n<candidates>");

        for (NodeResolutionInputV2.Candidate candidate : input.candidates()) {
            text.append("\nC").append(candidate.index()).append(". ")
                    .append(escape(singleLine(candidate.value())));
            for (NodeResolutionInputV2.Match match : candidate.matches()) {
                text.append("\n  - R").append(match.index()).append(". ")
                        .append(escape(singleLine(match.value())));
            }
        }
        text.append("\n</candidates>");
        if (!input.contextSubjects().isEmpty()) {
            text.append("\n<context-subjects>");
            for (NodeResolutionInputV2.ContextSubject subject : input.contextSubjects()) {
                text.append("\nR").append(subject.index()).append(". ")
                        .append(escape(singleLine(subject.value())));
            }
            text.append("\n</context-subjects>");
        }
        return text.toString();
    }

    private static String singleLine(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ");
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
