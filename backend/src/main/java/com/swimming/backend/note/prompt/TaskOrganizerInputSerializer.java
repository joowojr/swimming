package com.swimming.backend.note.prompt;

import com.swimming.backend.note.dto.out.TaskOrganizerInput;

public final class TaskOrganizerInputSerializer {
    private TaskOrganizerInputSerializer() {
    }

    public static String serialize(TaskOrganizerInput input) {
        StringBuilder xml = new StringBuilder();

        xml.append("<task-organizer-input>");

        appendMemo(xml, input);
        appendProjects(xml, input);
        appendTasks(xml, input);

        xml.append("</task-organizer-input>");

        return xml.toString();
    }

    private static void appendMemo(
            StringBuilder xml,
            TaskOrganizerInput input
    ) {
        xml.append("<memo><![CDATA[")
                .append(escapeCdata(input.memo()))
                .append("]]></memo>");
    }

    private static void appendProjects(
            StringBuilder xml,
            TaskOrganizerInput input
    ) {
        xml.append("<projects>");

        for (var project : input.projects()) {
            xml.append("<project id=\"")
                    .append(project.id())
                    .append("\">");

            xml.append("<name>")
                    .append(escapeXml(project.name()))
                    .append("</name>");

            xml.append("<description>")
                    .append(escapeXml(project.description()))
                    .append("</description>");

            xml.append("</project>");
        }

        xml.append("</projects>");
    }

    private static void appendTasks(
            StringBuilder xml,
            TaskOrganizerInput input
    ) {
        xml.append("<tasks>");

        for (var task : input.tasks()) {
            xml.append("<task folderId=\"")
                    .append(task.folderId())
                    .append("\">");

            xml.append("<title>")
                    .append(escapeXml(task.title()))
                    .append("</title>");

            xml.append("</task>");
        }

        xml.append("</tasks>");
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
