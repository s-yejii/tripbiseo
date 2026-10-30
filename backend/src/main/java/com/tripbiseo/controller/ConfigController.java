package com.tripbiseo.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 화면 설정 (카카오 지도 키) */
@RestController
public class ConfigController {

    // @Value: 환경변수나 application-local.properties의 KAKAO_JS_KEY 값을 읽음 (없으면 빈 글자)
    @Value("${KAKAO_JS_KEY:}")
    private String kakaoJsKey;

    /** 로그인 전에도 필요: 지도를 그릴 카카오 JavaScript 키(공개 키) */
    @GetMapping("/api/config/public")
    public Map<String, Object> publicConfig() {
        return kakaoJsKey.isBlank() ? Map.of() : Map.of("kakaoJsKey", kakaoJsKey);
    }
}
