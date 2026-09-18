package com.swimming.backend.common.client;

import com.swimming.backend.common.client.dto.SystemOneRequest;
import com.swimming.backend.common.client.dto.SystemOneResponse;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** TypeSafe의 HTTP 요청·응답 계약을 그대로 사용하는 클라이언트. */
public class TypeSafeClient {

    private final RestClient restClient;
    private final String apiKey;

    public TypeSafeClient(RestClient restClient, String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    public SystemOneResponse systemOne(SystemOneRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("TYPESAFE_API_KEY가 설정되지 않았습니다.");
        }
        return restClient.post()
                .uri("/v1/systemone")
                .headers(headers -> headers.setBearerAuth(apiKey))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(SystemOneResponse.class);
    }
}
