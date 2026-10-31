package com.tripbiseo.plan.service;

import com.tripbiseo.plan.dto.PlanChatRequest;
import com.tripbiseo.plan.dto.PlanChatResponse;
import com.tripbiseo.plan.dto.PlanGenerateRequest;
import com.tripbiseo.plan.dto.PlanGenerateResponse;

/** ③ AI 일정 만들기 (저장은 안 함, 저장은 ②의 TripService.createTrip) */
public interface PlanService {

    /** 사용자 말 → 여행 조건 정리 (Gemini). Gemini 오류는 API.md 공통 오류 그대로 */
    PlanChatResponse chatPlan(PlanChatRequest request);

    /** 조건 → 일정 초안. Gemini가 실패해도 오류 대신 notes에 적고 꼭 갈 곳만으로 초안 */
    PlanGenerateResponse generatePlan(long memberId, PlanGenerateRequest request);
}
