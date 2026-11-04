package com.tripbiseo.plan.dto;

import java.util.List;

import lombok.Data;

/** Gemini가 사용자 말에서 뽑은 여행 조건 (JSON 답 모양). 말하지 않은 값은 null */
@Data
public class AiConditions {
    private String reply;               // 조건을 정리했다는 한두 문장
    private String title;
    private String region;
    private String startDate;
    private String endDate;
    private Integer people;
    private List<String> transports;
    private List<String> mustVisit;
    private List<FixedVisit> fixed;
    private String style;
    private List<String> kinds;         // 새로 말한 장소 종류 코드 (food·cafe·bar·tour·culture·shop) — 기존에 더함 (11/03)
    private List<String> tags;          // 새로 말한 분위기 태그 코드 (mood·quiet·value·no_wait·solo) — 기존에 더함
    private List<String> needs;         // 새로 말한, 칩으로 못 담는 장소 요청 ("4명이 앉을 자리") — 기존에 더함 (11/04)
    private List<String> wishVisit;     // 새로 말한 가능하면 갈 곳 (11/04)
    private List<String> mustKinds;     // 새로 말한 꼭 넣을 종류 코드
    private List<String> avoidKinds;    // 새로 말한 빼 달라는 종류 코드
    private List<String> avoidVisit;    // 새로 말한 피하고 싶은 곳 이름
    private FixedVisit finish;          // 여행이 끝나는 곳·도착 시각
    private Integer maxWalkMin;         // 한 번에 걷는 시간 상한(분)
}
