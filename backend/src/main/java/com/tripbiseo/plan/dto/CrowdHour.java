package com.tripbiseo.plan.dto;

import lombok.Data;

/** 250m 칸의 요일·시각 평소 붐빔 (② 찬기 crowd_hour 읽기만, 11/13) */
@Data
public class CrowdHour {
    private Integer people;   // 평소 인원 (최근 8주 같은 요일·시각 평균)
    private Integer seoulPct; // 같은 요일·시각 서울 칸 중 순위 0~100 (높을수록 붐빔)
    private Integer selfPct;  // 이 칸이 가장 붐빌 때 대비 %
}
