package com.tripbiseo.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tripbiseo.plan.dto.PlanConditions;
import com.tripbiseo.plan.dto.PlanStay;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// 2일차부터 하루 시작 시각: 고른 시각(오전이면) + 숙소 체크아웃 중 빠른 쪽 (11/13)
class PlanRuleMakerDayStartTest {

    // 이 계산은 DB·다른 서비스를 안 써서 null로 만들어도 돼요
    private final PlanRuleMaker ruleMaker = new PlanRuleMaker(null, null);

    private PlanConditions tripOf(String startTime, String nights) {
        PlanConditions conditions = new PlanConditions();
        conditions.setStartDate("2026-11-14");
        conditions.setEndDate(nights);
        conditions.setStartTime(startTime);
        conditions.setStays(new ArrayList<>());
        return conditions;
    }

    private PlanStay stayOf(String checkInDate, String checkOutDate, String checkOutTime) {
        PlanStay stay = new PlanStay();
        stay.setCheckInDate(checkInDate);
        stay.setCheckOutDate(checkOutDate);
        stay.setCheckOutTime(checkOutTime);
        return stay;
    }

    @Test
    void 열한시반_시작_체크아웃_열한시면_2일차_열한시() {
        PlanConditions conditions = tripOf("11:30", "2026-11-15");
        conditions.getStays().add(stayOf("2026-11-14", "2026-11-15", "11:00"));
        assertEquals(11 * 60, ruleMaker.laterDayStartMin(conditions, 2));
    }

    @Test
    void 아홉시_시작_체크아웃_열한시면_2일차_아홉시() {
        PlanConditions conditions = tripOf("09:00", "2026-11-15");
        conditions.getStays().add(stayOf("2026-11-14", "2026-11-15", "11:00"));
        assertEquals(9 * 60, ruleMaker.laterDayStartMin(conditions, 2));
    }

    @Test
    void 오후_세시_시작_숙소_없으면_2일차_열시() {
        PlanConditions conditions = tripOf("15:00", "2026-11-15");
        assertEquals(10 * 60, ruleMaker.laterDayStartMin(conditions, 2));
    }

    @Test
    void 두_박이면_가운데_날은_체크아웃_없이_고른_시각() {
        PlanConditions conditions = tripOf("11:30", "2026-11-16");
        conditions.getStays().add(stayOf("2026-11-14", "2026-11-16", "11:00")); // 2박 연박: 체크아웃은 3일차
        assertEquals(11 * 60 + 30, ruleMaker.laterDayStartMin(conditions, 2));
        assertEquals(11 * 60, ruleMaker.laterDayStartMin(conditions, 3));
    }

    @Test
    void 시작_시각을_안_골랐으면_열시() {
        PlanConditions conditions = tripOf(null, "2026-11-15");
        conditions.setStays(List.of());
        assertEquals(10 * 60, ruleMaker.laterDayStartMin(conditions, 2));
    }
}
