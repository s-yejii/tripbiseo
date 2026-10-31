package com.tripbiseo.trip.dto;

import lombok.Data;

/** 일정 점검 검사 세 가지 {move, hours, closed} */
@Data
public class CheckResult {
    private boolean move;   // 지각 없음
    private boolean hours;  // 영업시간 OK
    private boolean closed; // 휴무 아님
}
