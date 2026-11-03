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
}
