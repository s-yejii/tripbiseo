package com.tripbiseo.trip.service;

import com.tripbiseo.trip.dto.PlanCheckResponse;
import com.tripbiseo.trip.dto.Trip;

/** ② 일정 점검 (예전에는 plan에 있었는데 ②로 옮김. 주소 /api/plans/check는 그대로) */
public interface PlanCheckService {

    /** 하루 일정 점검: 실제 이동시간·영업시간·휴무·고정 일정 여유. trip은 초안 또는 저장된 여행 */
    PlanCheckResponse checkPlan(Trip trip, int day);
}
