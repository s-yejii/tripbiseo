package com.tripbiseo.trip.dto;

import lombok.Data;

/** 일정 점검: 그날 첫 고정 일정 {name, time, arrive, slack} */
@Data
public class FixedCheck {
    private String name;
    private String time;    // 계획 시각
    private String arrive;  // 예상 도착
    private int slack;      // 여유(분), 음수면 지각. 키 이름이 slack
}
