package com.tripbiseo.plan.dto;

import java.util.List;

import lombok.Data;

/** POST /api/plans/route-options 응답: 이동 시간 짧은 순 후보 (최대 5개) */
@Data
public class RouteOptionsResponse {
    private int currentMinutes; // 지금 순서의 이동 시간 어림값
    private List<RouteOption> options;
}
