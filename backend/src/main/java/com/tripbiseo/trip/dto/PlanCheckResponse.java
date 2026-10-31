package com.tripbiseo.trip.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** POST /api/plans/check 응답 */
@Data
public class PlanCheckResponse {
    private int day;
    private String date;
    private boolean ok;             // 이동·영업시간·휴무 검사 모두 통과
    private FixedCheck fixed;       // 그날 첫 고정 일정, 없으면 null
    private Integer slackMin;       // fixed.slack과 같은 값, 고정 일정이 없으면 null
    private String finalArrive;     // 마지막 장소 예상 도착, 그날 칸이 없으면 null
    private CheckResult checks;
    private List<CheckedStop> stops = new ArrayList<>();
    private List<Leg> legs = new ArrayList<>(); // 칸 수 − 1개, coords 포함
}
