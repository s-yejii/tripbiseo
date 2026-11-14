package com.tripbiseo.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// AI 일정 칸 사이 이동 시간 어림 (11/12)
class PlanTravelTimeTest {

    private static final double[] EULJIRO_3GA = {37.5663, 126.9919};   // 을지로3가역
    private static final double[] SANCHEONG = {37.5658, 126.9906};     // 산청숯불가든 을지로 (약 130m)
    private static final double[] JONGNO_3GA = {37.5704, 126.9921};    // 종로3가역
    private static final double[] SUSHIDO_GANGNAM = {37.4952, 127.0850}; // 스시도 (강남 개포로, 약 12km)

    @Test
    void 바로_옆이면_걸어서_5분() {
        assertEquals(5, PlanTravelTime.minutesBetween(EULJIRO_3GA, SANCHEONG));
    }

    @Test
    void 종로에서_강남은_대중교통으로_40분_넘게() {
        int minutes = PlanTravelTime.minutesBetween(JONGNO_3GA, SUSHIDO_GANGNAM);
        assertTrue(minutes >= 40 && minutes <= 90, "종로3가 → 강남 개포로: " + minutes + "분");
    }

    @Test
    void 멀수록_같거나_오래_걸림() { // 11/13: 1km에서 거꾸로 되던 문제
        double[] start = {37.5600, 126.9800};
        int before = 0;
        for (int i = 1; i <= 40; i++) {
            double[] end = {37.5600 + i * 0.0009, 126.9800}; // 약 100m씩 멀어짐 (4km까지)
            int minutes = PlanTravelTime.minutesBetween(start, end);
            assertTrue(minutes >= before, (i * 100) + "m: " + minutes + "분 < 앞 " + before + "분");
            before = minutes;
        }
    }

    @Test
    void 좌표를_모르면_0분() {
        assertEquals(0, PlanTravelTime.minutesBetween(null, SANCHEONG));
    }
}
