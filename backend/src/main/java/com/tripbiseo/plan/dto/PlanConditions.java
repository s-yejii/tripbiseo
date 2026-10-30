package com.tripbiseo.plan.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * 여행 조건 (/plans/chat 응답, /plans/generate 요청).
 * title·region·mustVisit·fixed·style은 Gemini가 말하지 않으면 키가 없을 수 있어서 null인 칸은 응답에서 뺌
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlanConditions {
    private String title;
    private String region;              // "내 위치·여기·근처"라고 하면 "내 위치"
    private String startDate;           // 없으면 오늘
    private String endDate;             // 없으면 startDate
    private Integer people;             // 없으면 1
    private List<String> transports;    // 없으면 ["subway","bus","walk"]
    private List<String> mustVisit;     // 꼭 갈 곳 이름
    private List<FixedVisit> fixed;     // 시간이 정해진 일정
    private String style;               // 여행 스타일 한 줄
}
