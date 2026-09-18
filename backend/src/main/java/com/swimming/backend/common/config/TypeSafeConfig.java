package com.swimming.backend.common.config;

import com.swimming.backend.common.client.TypeSafeClient;
import com.swimming.backend.common.config.llm.TypeSafeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

@Configuration
public class TypeSafeConfig {

    /**
     * Spring AI의 {@code SimpleLoggerAdvisor}처럼 요청·응답 본문을 DEBUG로 남긴다.
     * 로컬 프로필에서만 이 로거를 켠다. 헤더는 남기지 않아 API 키가 로그에 실리지 않는다.
     */
    private static final Logger log = LoggerFactory.getLogger(TypeSafeClient.class);

    @Bean
    public TypeSafeClient typeSafeClient(RestClient.Builder builder, TypeSafeProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.responseTimeout());
        return new TypeSafeClient(builder.clone()
                .baseUrl(properties.baseUrl())
                // 응답 본문은 한 번만 읽힌다. 로그로 읽고도 역직렬화할 수 있게 DEBUG일 때만 버퍼링한다.
                .requestFactory(log.isDebugEnabled() ? new BufferingClientHttpRequestFactory(factory) : factory)
                .requestInterceptor(TypeSafeConfig::logExchange)
                .build(), properties.apiKey());
    }

    static ClientHttpResponse logExchange(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution
    ) throws IOException {
        if (!log.isDebugEnabled()) {
            return execution.execute(request, body);
        }
        log.debug("request: {} {} {}", request.getMethod(), request.getURI(),
                new String(body, StandardCharsets.UTF_8));
        ClientHttpResponse response = execution.execute(request, body);
        log.debug("response: {} {}", response.getStatusCode(),
                StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8));
        return response;
    }
}
