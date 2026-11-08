package com.tripbiseo.common.dev;

import java.util.ArrayList;
import java.util.List;

import com.tripbiseo.plan.dto.FixedVisit;
import com.tripbiseo.trip.dto.Stop;

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
    private Integer rainDay;     // 시연 비 예보 날 (일정 짤 때 알려 줌)
    private String rainFrom;     // 그날 비 시작 "13:00"
    private List<Stop> rainDayStops = new ArrayList<>(); // [반영하기]를 누르면 그날 일정을 이 순서로 (바깥 일정을 오전으로)
    private Integer overstayDay;   // 돌발: 이 날
    private String overstayName;   // 이 칸에서
    private Integer overstayMin;   // 계획보다 이만큼 더 머묾 (DEMO.md: 테디뵈르 24분)
    private String startTime;   // 1일차 시작 시각 "15:00": 로그인하면 시연 시계를 오늘 이 시각으로
}
