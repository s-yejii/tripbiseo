package com.tripbiseo.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.dto.Trip;
import com.tripbiseo.service.PlanService;

/** 일정 만들기 (저장 전 초안): 대화 → 조건 → 초안 → 검증 */
@RestController
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanService plans;

    public PlanController(PlanService plans) {
        this.plans = plans;
    }

    public record ChatRequest(String message, Map<String, Object> conditions) {}
    public record GenerateRequest(Map<String, Object> conditions, List<String> messages, Double lat, Double lng) {}

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody ChatRequest req) {
        return plans.chat(req.message(), req.conditions());
    }

    @PostMapping("/generate")
    public Map<String, Object> generate(@RequestBody GenerateRequest req) {
        return plans.generate(req.conditions(), req.messages(), req.lat(), req.lng());
    }

    /** 초안(또는 저장된 여행)의 하루를 검증 */
    @PostMapping("/check")
    public Map<String, Object> check(@RequestBody Trip draft, @RequestParam(defaultValue = "1") int day) {
        return plans.check(draft, day);
    }
}
