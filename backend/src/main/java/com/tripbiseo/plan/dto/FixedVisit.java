package com.tripbiseo.plan.dto;

import lombok.Data;

/** 시간이 정해진 일정 {name, time} (공연·예약) */
@Data
public class FixedVisit {
    private String name;
    private String time; // HH:mm
}
