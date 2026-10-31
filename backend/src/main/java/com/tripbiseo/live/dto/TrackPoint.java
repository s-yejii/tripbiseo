package com.tripbiseo.live.dto;

import lombok.Data;

/** GPS 점 하나 (⑤ 회고 track[]가 이 모양 그대로 씀) */
@Data
public class TrackPoint {
    private double lat;
    private double lng;
    private int day;      // 기록 날짜 − 시작 날짜 + 1
    private String time;  // HH:mm
}
