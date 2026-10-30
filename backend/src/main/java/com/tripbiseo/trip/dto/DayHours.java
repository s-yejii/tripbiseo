package com.tripbiseo.trip.dto;

import lombok.Data;

/** 요일별 영업시간 한 줄 (장소 상세 hours) */
@Data
public class DayHours {
    private String day;     // 월~일 또는 매일
    private String text;    // "10:00-22:00"
    private boolean today;  // 오늘 요일인지
}
