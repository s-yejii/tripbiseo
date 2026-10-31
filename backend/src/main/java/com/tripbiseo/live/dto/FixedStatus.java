package com.tripbiseo.live.dto;

import lombok.Data;

/** 남은 일정 중 첫 고정 일정 */
@Data
public class FixedStatus {
    private Long id;
    private String name;
    private String time;   // 계획 시각
    private Double lat;
    private Double lng;
    private String eta;    // 예상 도착
    private int slackMin;  // 여유(분), 음수면 지각
}
