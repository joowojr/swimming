package com.swimming.backend.knowledge.prompt;

import com.swimming.backend.knowledge.dto.out.CategorySuggestionInput;

import java.util.List;

public final class CategorySuggestionInputSerializer {

    private CategorySuggestionInputSerializer() {
    }

    public static String serialize(CategorySuggestionInput input) {
        StringBuilder xml = new StringBuilder();

        xml.append("<category-suggestion-input>");

        xml.append("<folder-name>")
                .append(escapeXml(input.folderName()))
                .append("</folder-name>");

        xml.append("<documents>");

        // 번호는 items에서의 자리다. 모델은 이 번호로 문서를 가리키고, 서버는 같은
        // 자리를 되짚어 문서로 되돌린다.
        List<CategorySuggestionInput.Item> items = input.items();
        for (int index = 0; index < items.size(); index++) {
            appendItem(xml, items.get(index), index + 1);
        }

        xml.append("</documents>");

        xml.append("</category-suggestion-input>");

        return xml.toString();
    }

    private static void appendItem(StringBuilder xml, CategorySuggestionInput.Item item, int index) {
        xml.append("<document index=\"").append(index).append("\">");

        xml.append("<title>").append(escapeXml(item.title())).append("</title>");
        xml.append("<summary>").append(escapeXml(item.summary())).append("</summary>");

        // 소화가 끝난 Source면 Topic이 반드시 있지만, 없는 것을 빈 요소로 보내지는 않는다.
//        if (item.topic() != null) {
//            xml.append("<topic>").append(escapeXml(item.topic())).append("</topic>");
//        }

//        appendSubjects(xml, item.subjects());

        xml.append("</document>");
    }

    /** 걸린 것이 없으면 요소 자체를 넣지 않는다. 빈 목록은 모델에게 읽을 거리만 늘린다. */
    private static void appendSubjects(StringBuilder xml, List<String> subjects) {
        if (subjects.isEmpty()) {
            return;
        }

        xml.append("<subjects>");

        for (String subject : subjects) {
            xml.append("<subject>").append(escapeXml(subject)).append("</subject>");
        }

        xml.append("</subjects>");
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
