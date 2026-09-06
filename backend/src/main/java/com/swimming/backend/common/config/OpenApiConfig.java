package com.swimming.backend.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    /**
     * 문서 전체에 JWT 인증을 걸어 Swagger UI의 Authorize로 토큰을 넣고 실제 호출을 해 볼 수
     * 있게 한다.
     *
     * <p>전역으로 거는 이유는 이 API가 인증을 기본으로 삼기 때문이다
     * ({@code anyRequest().authenticated()}). 열려 있는 쪽이 예외이므로, 열린 엔드포인트에만
     * {@code @SecurityRequirements}로 해제 표시를 단다.
     */
    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Swimming API")
                        .version("v0.4")
                        .description("""
                                각 태그의 설명에 그 API를 쓰는 화면을 적어 두었다.

                                인증이 필요한 요청은 `Authorize`에 access token을 넣고 호출한다.
                                토큰은 `POST /api/auth/...`로 받는다.
                                """))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("access token. `Bearer ` 접두사는 붙이지 않는다.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
