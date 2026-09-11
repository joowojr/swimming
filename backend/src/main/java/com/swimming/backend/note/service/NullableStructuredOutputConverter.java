package com.swimming.backend.note.service;

import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.StructuredOutputConverter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Spring AI가 선택 필드로 생성한 속성을 OpenAI strict JSON Schema가 요구하는
 * required + nullable 형태로 변환한다.
 */
public final class NullableStructuredOutputConverter<T> implements StructuredOutputConverter<T> {

    private static final JsonMapper JSON_MAPPER = JsonMapper.shared();

    private final BeanOutputConverter<T> delegate;
    private final String jsonSchema;

    public NullableStructuredOutputConverter(Class<T> type, String... nullablePropertyNames) {
        this.delegate = new BeanOutputConverter<>(type);
        this.jsonSchema = makeStrictNullable(
                delegate.getJsonSchema(),
                new HashSet<>(Arrays.asList(nullablePropertyNames))
        );
    }

    @Override
    public T convert(String source) {
        return delegate.convert(source);
    }

    @Override
    public String getFormat() {
        return delegate.getFormat().replace(delegate.getJsonSchema(), jsonSchema);
    }

    @Override
    public String getJsonSchema() {
        return jsonSchema;
    }

    private String makeStrictNullable(String source, Set<String> nullablePropertyNames) {
        try {
            JsonNode schema = JSON_MAPPER.readTree(source);
            makeStrictNullable(schema, nullablePropertyNames);
            return JSON_MAPPER.writeValueAsString(schema);
        } catch (JacksonException exception) {
            throw new IllegalStateException("구조화 출력 스키마를 변환할 수 없습니다.", exception);
        }
    }

    private void makeStrictNullable(JsonNode node, Set<String> nullablePropertyNames) {
        if (!node.isObject()) {
            return;
        }

        ObjectNode object = (ObjectNode) node;
        JsonNode properties = object.get("properties");
        if (properties instanceof ObjectNode propertyObject) {
            nullablePropertyNames.forEach(propertyName -> {
                JsonNode property = propertyObject.get(propertyName);
                if (property instanceof ObjectNode propertySchema) {
                    addNullType(propertySchema);
                    addRequiredProperty(object, propertyName);
                }
            });
        }

        object.valueStream().forEach(child -> makeStrictNullable(child, nullablePropertyNames));
    }

    private void addNullType(ObjectNode propertySchema) {
        JsonNode currentType = propertySchema.get("type");
        ArrayNode types = JSON_MAPPER.createArrayNode();
        if (currentType != null && currentType.isArray()) {
            currentType.forEach(types::add);
        } else if (currentType != null) {
            types.add(currentType.asText());
        }
        if (!types.valueStream().anyMatch(type -> "null".equals(type.asText()))) {
            types.add("null");
        }
        propertySchema.set("type", types);
    }

    private void addRequiredProperty(ObjectNode objectSchema, String propertyName) {
        JsonNode currentRequired = objectSchema.get("required");
        ArrayNode required;
        if (currentRequired instanceof ArrayNode arrayNode) {
            required = arrayNode;
        } else {
            required = JSON_MAPPER.createArrayNode();
            objectSchema.set("required", required);
        }
        if (!required.valueStream().anyMatch(value -> propertyName.equals(value.asText()))) {
            required.add(propertyName);
        }
    }
}
