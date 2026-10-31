package com.tripbiseo.trip.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/** 영업 정보: 도착 예상 시각 기준. open만 항상 있고, 나머지는 상황에 따라 키가 없음 */
@Data
public class OpenInfo {
    private Boolean open;          // 도착할 때 열려 있나. 영업시간 정보가 없으면 null
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String closedReason;   // 닫혀 있을 때만: "오늘 휴무 (매주 화요일)" 등
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String closeAt;        // 영업시간을 알고 24시간 영업이 아닐 때: HH:mm
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer minutesLeft;   // 열려 있을 때: 도착 후 닫을 때까지 남은 분
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean closingSoon;   // 열려 있을 때: 남은 분 ≤ max(60, 머무는 분)
}
