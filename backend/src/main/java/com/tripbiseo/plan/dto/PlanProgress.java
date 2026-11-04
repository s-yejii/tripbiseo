package com.tripbiseo.plan.dto;

import lombok.Data;

/**
 * 일정 만들기 진행 한 단계 (POST /api/plans/generate/stream 이 단계가 끝날 때마다 보냄, 11/04)
 * 예: {step: "candidates", state: "done", percent: 20, text: "장소 후보 52곳 찾기", detail: null}
 */
@Data
public class PlanProgress {
    private String step;    // candidates · reviews · ai · rules · fixed
    private String state;   // now(하는 중) · done(끝)
    private int percent;    // 0~100
    private String text;    // 화면에 보일 단계 이름
    private String detail;  // 한 줄 결과 (없으면 null)
}
