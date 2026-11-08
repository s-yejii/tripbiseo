package com.tripbiseo.common.dev;

import java.util.ArrayList;
import java.util.List;

import com.tripbiseo.plan.dto.FixedVisit;

import lombok.Data;

/** 시연 계정 AI 일정 화면에 미리 채워 둘 것 (11/08): 출발지 · 이미 잡아 둔 예약 · 숙소 */
@Data
public class ScenarioSetup {
    private String originName;
    private String originAddress;
    private Double originLat;
    private Double originLng;
    private List<FixedVisit> fixed = new ArrayList<>(); // 이미 예약한 시간 고정 일정 (KTX)
    private String stayName;
    private String stayAddress;
    private Double stayLat;
    private Double stayLng;
    private String stayCheckInTime;
    private String stayCheckOutTime;
    private String startTime;   // 1일차 시작 시각 "15:00": 로그인하면 시연 시계를 오늘 이 시각으로
}
