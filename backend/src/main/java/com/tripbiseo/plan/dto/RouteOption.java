package com.tripbiseo.plan.dto;

import java.util.List;

import lombok.Data;

/** 동선 후보 하나 (POST /api/plans/route-options) */
@Data
public class RouteOption {
    private List<Integer> order; // 자리마다 들어갈 원래 번호 (0부터)
    private int minutes;         // 이동 시간 어림값 (외부 길찾기 안 부름)
    private long meters;         // 직선 거리 합
}
