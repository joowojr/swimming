package com.swimming.backend.note.prompt;

import com.swimming.backend.note.dto.out.TaskOrganizerInput;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class TaskOrganizerInputSerializer {
    private TaskOrganizerInputSerializer() {
    }

    public static String serialize(TaskOrganizerInput input) {
        StringBuilder xml = new StringBuilder();

        xml.append("<task-organizer-input>");

        appendMemo(xml, input);
        appendCurrentDate(xml, input);
        appendFolders(xml, input);

        xml.append("</task-organizer-input>");

        return xml.toString();
    }

    /**
     * 폴더가 이미 정해진 추출 경로용. 폴더와 기존 task 를 넣지 않는다.
     */
    public static String serializeMemoOnly(TaskOrganizerInput input) {
        StringBuilder xml = new StringBuilder();

        xml.append("<task-organizer-input>");
        appendMemo(xml, input);
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

    private static void appendCurrentDate(
            StringBuilder xml,
            TaskOrganizerInput input
    ) {
        if (input.currentDate() != null) {
            xml.append("<current-date>")
                    .append(input.currentDate())
                    .append("</current-date>");
        }
    }

    private static void appendFolders(
            StringBuilder xml,
            TaskOrganizerInput input
    ) {
        Map<Long, List<String>> taskTitlesByFolderId = input.tasks().stream()
                .collect(Collectors.groupingBy(
                        task -> task.folderId(),
                        Collectors.mapping(task -> task.title(), Collectors.toList())
                ));

        xml.append("<folders>");

        for (int folderIndex = 0; folderIndex < input.folders().size(); folderIndex++) {
            if (folderIndex > 0) {
                xml.append('\n');
            }

            var folder = input.folders().get(folderIndex);
            xml.append("<folder id=\"")
                    .append(folder.id())
                    .append("\">");

            xml.append("<name>")
                    .append(escapeXml(folder.name()))
                    .append("</name>");

            xml.append("<description>")
                    .append(escapeXml(folder.description()))
                    .append("</description>");

            List<String> taskTitles = taskTitlesByFolderId.getOrDefault(
                    folder.id(),
                    List.of()
            );
            if (!taskTitles.isEmpty()) {
                xml.append("<tasks>")
                        .append(taskTitles.stream()
                                .map(TaskOrganizerInputSerializer::escapeXml)
                                .collect(Collectors.joining("\n")))
                        .append("</tasks>");
            }

            xml.append("</folder>");
        }

        xml.append("</folders>");
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
