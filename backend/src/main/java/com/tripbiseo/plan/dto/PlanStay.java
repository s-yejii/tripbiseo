package com.tripbiseo.plan.dto;

import lombok.Data;

/** 여행 정보에서 담은 숙소 하나 (일정 만들기 조건). 체크인 날 밤은 여기서 자고, 다음 날은 여기서 출발해요 */
@Data
public class PlanStay {
    private String name;
    private String address;
    private Double lat;
    private Double lng;
    private String checkInDate;   // yyyy-MM-dd
    private String checkInTime;   // HH:mm
    private String checkOutDate;  // yyyy-MM-dd
    private String checkOutTime;  // HH:mm
}
