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
}
