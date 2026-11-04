package com.tripbiseo.plan.dto;

import java.util.List;

import lombok.Data;

/** POST /api/plans/route-order 응답 */
@Data
public class RouteOrderResponse {
    private List<Integer> order;   // 자리마다 들어갈 원래 번호 (0부터)
    private double beforeMeters;   // 바꾸기 전 직선 거리 합
    private double afterMeters;    // 바꾼 뒤 직선 거리 합
}
