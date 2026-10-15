package com.tripbiseo.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.service.ApiKeyService;

/** 화면 설정(카카오 지도 키) + 개발용 API 키 관리(관리자) */
@RestController
public class ConfigController {

    private final ApiKeyService keys;

    public ConfigController(ApiKeyService keys) {
        this.keys = keys;
    }

    /** 로그인 전에도 필요: 지도를 그릴 카카오 JavaScript 키(공개 키) */
    @GetMapping("/api/config/public")
    public Map<String, Object> publicConfig() {
        String k = keys.get("KAKAO_JS_KEY");
        return k == null ? Map.of() : Map.of("kakaoJsKey", k);
    }

    @GetMapping("/api/admin/keys")
    public List<Map<String, Object>> list() {
        return keys.list();
    }

    public record SaveRequest(Map<String, String> keys) {}

    @PutMapping("/api/admin/keys")
    public List<Map<String, Object>> save(@RequestBody SaveRequest req) {
        return keys.save(req.keys());
    }
}
