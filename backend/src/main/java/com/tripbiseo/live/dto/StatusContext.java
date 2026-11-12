package com.tripbiseo.live.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.tripbiseo.trip.dto.OpenInfo;
import com.tripbiseo.trip.dto.Stop;

import lombok.Data;

/**
 * 지금 상황 계산 결과 묶음. StatusServiceImpl이 채우고 LiveAlertMaker가 읽어서 알림을 만들어요.
 * (화면에 보내지 않는 서버 안쪽 DTO)
 */
@Data
public class StatusContext {
    private int now;                                     // 지금 시각 (자정부터 센 분)
    private List<Stop> todayStops = new ArrayList<>();   // 오늘 칸 전부
    private Stop current;                                // 지금 있는 칸, null 가능
    private Integer leaveAt;                             // 지금 칸에서 떠날 시각(분), 지금 칸이 없으면 null
    private Stop nextStop;                               // 다음 남은 칸, null 가능
    private Stop fixedStop;                              // 남은 칸 중 첫 고정 일정, null 가능
    private Integer slack;                               // 고정 일정 여유(분), 음수면 지각. 고정 일정이 없으면 null
    private List<Stop> missedStops = new ArrayList<>();  // 남은 칸 중 지난 일정
    private Map<Long, Integer> etaMap = new HashMap<>(); // 칸 id → 예상 도착(분)
    private Map<Long, OpenInfo> openMap = new HashMap<>(); // 칸 id → 영업 정보
    private WeatherInfo weather;                         // 다음 경로 비 소식, 없으면 null (LiveWeatherChecker가 채움)
    private Integer fastestSlack;                        // 고정 일정에 늦을 때 가장 빠른 방법으로 지금 가도 남는 여유(분), 음수면 못 닿음. 안 늦으면 null (11/12)
    private boolean fixedMeal;                           // 고정 일정이 식당·카페·술집인지 (놓침 문구 "예약 시각"/"약속 시각", 11/12)
}
