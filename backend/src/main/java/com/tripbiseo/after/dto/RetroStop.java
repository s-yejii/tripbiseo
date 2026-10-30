package com.tripbiseo.after.dto;

import lombok.Data;

/** 회고 stops[]: 일정 칸 (skipped 포함) + 실제 도착·머문 시간 */
@Data
public class RetroStop {
    private Long id;
    private Integer day;
    private String name;
    private String category;
    private Double lat;
    private Double lng;
    private String time;          // 계획 시각
    private Integer stay;         // 계획 머무는 분
    private String state;         // todo / done / skipped
    private String actualArrive;  // 실제 도착 HH:mm, 안 갔으면 null
    private Integer actualStay;   // 실제 머문 분, 출발 기록이 없으면 null
}
