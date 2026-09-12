package com.swimming.backend.knowledge.prompt;

import com.swimming.backend.knowledge.dto.out.NodeResolutionInput;

public final class NodeResolutionInputSerializer {

    private NodeResolutionInputSerializer() {
    }

    public static String serialize(NodeResolutionInput input) {
        StringBuilder xml = new StringBuilder("<node-resolution-input>");

        xml.append("<summary>")
                .append(escapeXml(input.summary()))
                .append("</summary>");

        xml.append("<candidates>");
        for (NodeResolutionInput.Candidate candidate : input.candidates()) {
            xml.append("<candidate index=\"")
                    .append(candidate.index())
                    .append("\">")
                    .append(escapeXml(candidate.value()))
                    .append("</candidate>");
        }
        xml.append("</candidates>");

        if (!input.existingSubjects().isEmpty()) {
            xml.append("<existing-subjects>");
            for (NodeResolutionInput.ExistingSubject subject : input.existingSubjects()) {
                xml.append("<subject index=\"")
                        .append(subject.index())
                        .append("\">")
                        .append(escapeXml(subject.value()))
                        .append("</subject>");
            }
            xml.append("</existing-subjects>");
        }

        return xml.append("</node-resolution-input>").toString();
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
