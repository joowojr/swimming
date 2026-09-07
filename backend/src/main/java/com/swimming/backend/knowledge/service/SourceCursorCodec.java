package com.swimming.backend.knowledge.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Source 목록의 다음 페이지 위치를 문자열 하나로 감싼다.
 *
 * <p>정렬이 생성 시각 내림차순인데, 링크 여러 개를 한 번에 저장하면 같은 밀리초에 여러 행이
 * 생긴다. 시각만으로는 경계에서 행이 겹치거나 빠지므로 노드 id를 함께 싣는다.
 */
public final class SourceCursorCodec {

    private static final String VERSION = "v1";

    private SourceCursorCodec() {
    }

    public static String encode(Instant createdAt, UUID nodeId) {
        String payload = String.join("|", VERSION, createdAt.toString(), nodeId.toString());

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public static Decoded decode(String cursor) {
        try {
            String payload = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] fields = payload.split("\\|", -1);

            if (fields.length != 3 || !VERSION.equals(fields[0])) {
                throw new BusinessException(ErrorCode.INVALID_KNOWLEDGE_CURSOR);
            }

            return new Decoded(Instant.parse(fields[1]), UUID.fromString(fields[2]));
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_KNOWLEDGE_CURSOR);
        }
    }

    public record Decoded(Instant createdAt, UUID nodeId) {
    }
}
