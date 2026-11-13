package com.tripbiseo.plan.service;

import com.tripbiseo.common.util.Geo;

/**
 * 두 장소 사이 이동 시간 어림 (③, 11/12). AI 일정 칸 사이를 거리와 상관없이 20분으로 두던 것을 바꿈
 * (종로에서 12:30에 끝나고 같은 12:30에 강남 예약이 잡힐 수 있었어요).
 * 직선거리 × 1.3(길이 돌아가는 만큼) → 1km 안이면 걸어서(분속 75m), 그보다 멀면 대중교통(분속 280m + 역까지 걷고 기다리는 10분).
 * 실제 길찾기(RouteClient)는 칸마다 부르면 느리고 키가 없을 때도 있어서, 일정 만들 때는 이 어림값을 씀
 */
public final class PlanTravelTime {

    private static final double DETOUR = 1.3;              // 직선거리보다 실제 길이 이만큼 김
    private static final int WALK_LIMIT_M = 1000;          // 직선 1km 안은 걸어감
    private static final double WALK_M_PER_MIN = 75;       // 시속 4.5km
    private static final double TRANSIT_M_PER_MIN = 280;   // 지하철·버스 평균 시속 약 17km
    private static final int TRANSIT_EXTRA_MIN = 10;       // 역까지 걷고 기다리는 시간
    private static final int SHORTEST_MOVE_MIN = 5;        // 바로 옆이어도 나가고 들어가는 시간

    private PlanTravelTime() {
    }

    /** 두 점 사이 이동 분 (어느 한쪽 좌표를 모르면 0) */
    public static int minutesBetween(double[] from, double[] to) {
        if (from == null || to == null) {
            return 0;
        }
        double straight = Geo.distanceMeters(from[0], from[1], to[0], to[1]);
        double road = straight * DETOUR;
        int minutes = straight <= WALK_LIMIT_M
                ? (int) Math.ceil(road / WALK_M_PER_MIN)
                : (int) Math.ceil(road / TRANSIT_M_PER_MIN) + TRANSIT_EXTRA_MIN;
        return Math.max(SHORTEST_MOVE_MIN, minutes);
    }
}
