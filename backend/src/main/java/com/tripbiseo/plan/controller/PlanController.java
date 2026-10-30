package com.tripbiseo.plan.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.common.auth.LoginUser;
import com.tripbiseo.plan.dto.PlanChatRequest;
import com.tripbiseo.plan.dto.PlanChatResponse;
import com.tripbiseo.plan.dto.PlanGenerateRequest;
import com.tripbiseo.plan.dto.PlanGenerateResponse;
import com.tripbiseo.plan.service.PlanService;

import lombok.RequiredArgsConstructor;

/** ③ AI 일정 만들기 (저장 전 초안): 대화 → 조건 → 초안. 점검(/api/plans/check)은 ②의 PlanCheckController */
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;
    private final LoginUser loginUser;

    /** 사용자 말 → 여행 조건 정리 */
    @PostMapping("/chat")
    public PlanChatResponse chatPlan(@RequestBody PlanChatRequest request) {
        return planService.chatPlan(request);
    }

    /** 조건 → 일정 초안 (저장 안 함) */
    @PostMapping("/generate")
    public PlanGenerateResponse generatePlan(@RequestBody PlanGenerateRequest request) {
        return planService.generatePlan(loginUser.id(), request);
    }
}
