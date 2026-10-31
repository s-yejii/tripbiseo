package com.tripbiseo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/**
 * Swagger 화면(/swagger-ui.html)의 제목. API 목록은 Controller를 보고 자동으로 만들어져요.
 * 로그인이 필요한 API는 Swagger에서 먼저 POST /api/auth/login을 실행하면 같은 브라우저 쿠키로 불러져요.
 * 기준 문서는 docs/API.md (둘이 다르면 API.md가 기준)
 */
@Configuration
public class SwaggerConfig {

    /** Swagger 화면 맨 위에 보일 이름·설명 */
    @Bean
    public OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("여행어때 API")
                .description("기준 문서는 docs/API.md. 로그인이 필요하면 먼저 POST /api/auth/login을 실행하세요.")
                .version("v1"));
    }
}
