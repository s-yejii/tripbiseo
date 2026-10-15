package com.tripbiseo.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.service.GeminiService;

/** 팀 Gemini 테스트용 (그대로 유지). {message} → {reply} */
@RestController
@RequestMapping("/api/chat")
public class GeminiController {

    private final GeminiService geminiService;

    public GeminiController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @PostMapping
    public Map<String, String> chat(@RequestBody Map<String, String> request) {
        return Map.of("reply", geminiService.askText(request.getOrDefault("message", "")));
    }
}
