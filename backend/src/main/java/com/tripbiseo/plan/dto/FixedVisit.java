package com.tripbiseo.plan.dto;

import lombok.Data;

/** 시간이 정해진 일정 {name, time} (공연·예약) */
@Data
public class FixedVisit {
    private String name;
    private String time; // HH:mm
    private Integer day; // 몇째 날 (1부터). 없으면 1일차 (11/04: 여러 날 여행에서 날 고르기)
}
