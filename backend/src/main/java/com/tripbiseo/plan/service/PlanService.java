package com.tripbiseo.plan.service;

import java.util.function.Consumer;

import com.tripbiseo.plan.dto.PlanChatRequest;
import com.tripbiseo.plan.dto.PlanChatResponse;
import com.tripbiseo.plan.dto.PlanGenerateRequest;
import com.tripbiseo.plan.dto.PlanGenerateResponse;
import com.tripbiseo.plan.dto.PlanProgress;

/** ③ AI 일정 만들기 (저장은 안 함, 저장은 ②의 TripService.createTrip) */
public interface PlanService {

    /** 사용자 말 → 여행 조건 정리 (Gemini). Gemini 오류는 API.md 공통 오류 그대로 */
    PlanChatResponse chatPlan(PlanChatRequest request);

    /** 조건 → 일정 초안. Gemini가 실패해도 오류 대신 notes에 적고 꼭 갈 곳만으로 초안 */
    PlanGenerateResponse generatePlan(long memberId, PlanGenerateRequest request);

    /**
     * 위와 같고, 단계가 끝날 때마다 progress로 알림 (/generate/stream, 11/04).
     * Consumer: 값 하나를 받아서 처리하는 함수 모양 (자바 기본). 컨트롤러가 받은 단계를 화면으로 바로 보냄
     */
    PlanGenerateResponse generatePlan(long memberId, PlanGenerateRequest request, Consumer<PlanProgress> progress);
}
