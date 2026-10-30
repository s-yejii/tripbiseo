package com.tripbiseo.trip.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.trip.dto.PlanCheckResponse;
import com.tripbiseo.trip.dto.Trip;
import com.tripbiseo.trip.service.PlanCheckService;

import lombok.RequiredArgsConstructor;

/** ② 일정 점검. 주소는 /api/plans/check 그대로지만 담당은 ② (③의 PlanController와 따로) */
@RestController
@RequiredArgsConstructor
public class PlanCheckController {

    private final PlanCheckService planCheckService;

    /** 하루 일정 점검 (day 기본 1). 본문은 초안 또는 저장된 여행 */
    @PostMapping("/api/plans/check")
    public PlanCheckResponse checkPlan(@RequestBody Trip trip, @RequestParam(defaultValue = "1") int day) {
        return planCheckService.checkPlan(trip, day);
    }
}
