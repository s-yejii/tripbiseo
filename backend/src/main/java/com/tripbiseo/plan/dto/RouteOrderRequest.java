package com.tripbiseo.plan.dto;

import java.util.List;

import com.tripbiseo.trip.dto.Stop;

import lombok.Data;

/** POST /api/plans/route-order 요청: 하루 일정과 출발점 */
@Data
public class RouteOrderRequest {
    private List<Stop> stops;  // 그날 일정 (시각 순)
    private String date;       // 그날 날짜 yyyy-MM-dd (영업 확인, 없으면 확인 안 함)
    private Double startLat;   // 출발점 (없으면 null)
    private Double startLng;
    private List<String> transports; // 이동 수단 (이동 시간 어림값에 씀, 없으면 지하철·버스·도보)
}
