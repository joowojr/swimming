package com.swimming.backend.task.dto.in;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.util.Optional;

/**
 * 필드를 보냈는지까지 담아 id를 읽는다. 필드가 없으면 null, {@code null}을 보내면 빈 Optional, 값을 보내면 그 값이다.
 *
 * <p>Jackson의 기본 Optional 처리는 필드가 없을 때도 빈 Optional을 넣어 "안 보냄"과 "null로 보냄"을 구분하지 못한다.
 */
public class PresenceAwareIdDeserializer extends ValueDeserializer<Optional<Long>> {

    @Override
    public Optional<Long> deserialize(JsonParser parser, DeserializationContext context) {
        return Optional.of(context.readValue(parser, Long.class));
    }

    @Override
    public Optional<Long> getNullValue(DeserializationContext context) {
        return Optional.empty();
    }

    @Override
    public Object getAbsentValue(DeserializationContext context) {
        return null;
    }
}
