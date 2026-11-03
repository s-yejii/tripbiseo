package com.tripbiseo.plan.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.tripbiseo.common.util.Geo;
import com.tripbiseo.plan.dto.AiDraftStop;
import com.tripbiseo.plan.dto.PlanConditions;
import com.tripbiseo.plan.dto.PlanGenerateResponse;
import com.tripbiseo.plan.dto.PlanStay;
import com.tripbiseo.trip.dto.Place;
import com.tripbiseo.trip.dto.Stop;

/**
 * ③ 규칙으로 일정 짜기 (11/03). Gemini가 없거나 응답이 없을 때도 고른 종류·평점·거리로 하루 일정을 만들어요.
 *   하루 틀: 정해 둔 칸(10:00 관광·전시·쇼핑, 12:00 점심, 13:40 카페 …)마다 고른 종류 후보 중 "점수 − 거리" 가 가장 큰 곳을 차례로
 *   점수 = 보정 평점 + 고른 태그 가산점 (PlanCandidateFinder.scoreOf), 거리는 앞 장소(첫 장소는 출발지·숙소)에서 1km마다 0.3점 뺌
 * 숙소: 체크인 날 마지막 일정으로 넣고, 다음 날 첫 장소는 숙소에서 가까운 곳부터 (addStayStops · dayStartPoint)
 */
@Component
public class PlanRuleMaker {

    private static final double DISTANCE_PENALTY_PER_KM = 0.3; // 평점 0.3점 = 1km 더 가는 것과 같게 봄
    private static final int TODAY_START_AFTER_MIN = 60;       // 오늘 출발이면 지금부터 1시간 뒤 칸부터
    private static final int STAY_MIN = 30;                    // 숙소 칸 머무는 시간 (일정표 표시용)
    private static final int STAY_AFTER_LAST_MIN = 30;         // 마지막 일정이 끝나고 숙소까지 여유
    private static final int LATEST_STAY_MIN = 23 * 60 + 30;   // 숙소 칸은 늦어도 23:30
    private static final List<String> DEFAULT_KINDS = List.of("food", "cafe");

    // 하루 칸: "시각/머무는 분/이 칸에 넣을 종류(앞이 먼저)". 고른 종류가 하나도 없는 칸은 건너뜀
    // 칸 사이는 이동 20~40분을 비워 둠 (10:00~11:30 → 12:00 점심)
    private static final List<String> DAY_SLOTS = List.of(
            "10:00/90/tour,culture,shop",
            "12:00/70/food",
            "13:40/60/cafe,culture,tour",
            "15:10/80/culture,tour,shop",
            "16:50/60/shop,cafe,tour",
            "18:30/80/food",
            "20:30/90/bar");
    // 한 종류만 골랐을 때는 그 종류로 하루 3곳
    private static final Map<String, List<String>> SINGLE_SLOTS = Map.of(
            "food", List.of("11:30/70", "15:00/50", "18:30/80"),
            "cafe", List.of("11:00/60", "14:00/60", "17:00/60"),
            "bar", List.of("18:00/90", "20:00/90", "22:00/60"),
            "tour", List.of("10:30/90", "13:30/90", "16:00/90"),
            "culture", List.of("10:30/90", "13:30/90", "16:00/80"),
            "shop", List.of("11:00/80", "14:00/80", "16:30/80"));

    /** 고른 종류 (없으면 맛집·카페) */
    public List<String> kindsOf(PlanConditions conditions) {
        return conditions.getKinds() == null || conditions.getKinds().isEmpty() ? DEFAULT_KINDS : conditions.getKinds();
    }

    /** 날마다 시각 칸을 채운 일정 (후보 id로 고름). 이미 정한 일정(꼭 갈 곳·고정)은 그대로 두고 뒤에 붙임 */
    public List<AiDraftStop> makeStops(PlanConditions conditions, Map<String, Place> candidates, Map<String, Double> scores,
                                       int days, String startDate, List<AiDraftStop> already) {
        List<AiDraftStop> stops = new ArrayList<>(already);
        Set<String> used = new HashSet<>();
        List<String[]> slots = slotsOf(kindsOf(conditions));
        boolean isToday = startDate.equals(Geo.now().toLocalDate().toString());
        for (int day = 1; day <= days; day++) {
            double[] point = dayStartPoint(conditions, startDate, day, candidates);
            for (String[] slot : slots) {
                if (day == 1 && isToday && Geo.toMinutes(slot[1]) < Geo.nowMinutes() + TODAY_START_AFTER_MIN) {
                    continue; // 이미 지난 시각
                }
                Place best = null;
                for (String kind : slot[0].split(",")) { // 이 칸에 넣을 종류를 앞에서부터
                    best = best != null ? best : pickBest(candidates, scores, used, kind, point);
                }
                if (best == null) {
                    continue; // 넣을 종류 후보가 다 떨어짐
                }
                used.add(best.getId());
                stops.add(toDraftStop(best, day, slot[1], Integer.parseInt(slot[2])));
                point = new double[] {best.getLat(), best.getLng()};
            }
        }
        return stops;
    }

    /** 숙소: 체크인 날 마지막 일정 뒤에 숙소 칸 (마지막 일정 끝 + 30분, 체크인 시각보다 이르지 않게, 23:30까지) */
    public void addStayStops(PlanGenerateResponse response, PlanConditions conditions, int days) {
        if (conditions.getStays() == null) {
            return;
        }
        for (PlanStay stay : conditions.getStays()) {
            int day = dayOf(response.getStartDate(), stay.getCheckInDate());
            if (day < 1 || day > days || stay.getLat() == null) {
                continue; // 여행 기간 밖이거나 위치가 없는 숙소
            }
            int clock = stay.getCheckInTime() == null ? 0 : Geo.toMinutes(stay.getCheckInTime());
            for (Stop stop : response.getStops()) {
                if (stop.getDay() == day) {
                    clock = Math.max(clock, Geo.toMinutes(stop.getTime()) + stop.getStay() + STAY_AFTER_LAST_MIN);
                }
            }
            response.getStops().add(toStayStop(stay, day, Math.min(clock, LATEST_STAY_MIN)));
        }
    }

    // ---------------------------------------------------------------- 도우미

    /** 하루 칸 목록: [넣을 종류들(쉼표), "HH:mm", 머무는 분]. 고른 종류만 남기고, 하나도 없는 칸은 뺌 */
    private List<String[]> slotsOf(List<String> kinds) {
        List<String[]> slots = new ArrayList<>();
        if (kinds.size() == 1) {
            for (String slot : SINGLE_SLOTS.getOrDefault(kinds.get(0), List.of())) {
                String[] parts = slot.split("/"); // "12:00/70" → [12:00, 70]
                slots.add(new String[] {kinds.get(0), parts[0], parts[1]});
            }
            return slots;
        }
        for (String slot : DAY_SLOTS) {
            String[] parts = slot.split("/"); // "13:40/60/cafe,culture,tour" → [13:40, 60, cafe,culture,tour]
            List<String> chosen = new ArrayList<>();
            for (String kind : parts[2].split(",")) {
                if (kinds.contains(kind)) {
                    chosen.add(kind);
                }
            }
            if (!chosen.isEmpty()) {
                slots.add(new String[] {String.join(",", chosen), parts[0], parts[1]});
            }
        }
        return slots;
    }

    /** 그날 첫 출발점: 1일차는 출발지, 그 뒤는 전날 숙소, 없으면 지역 중심 → 첫 후보 */
    private double[] dayStartPoint(PlanConditions conditions, String startDate, int day, Map<String, Place> candidates) {
        if (day == 1 && conditions.getOriginLat() != null) {
            return new double[] {conditions.getOriginLat(), conditions.getOriginLng()};
        }
        if (day > 1 && conditions.getStays() != null) {
            for (PlanStay stay : conditions.getStays()) {
                if (stay.getLat() != null && dayOf(startDate, stay.getCheckInDate()) == day - 1) {
                    return new double[] {stay.getLat(), stay.getLng()};
                }
            }
        }
        if (conditions.getAreaLat() != null) {
            return new double[] {conditions.getAreaLat(), conditions.getAreaLng()};
        }
        Place first = candidates.isEmpty() ? null : candidates.values().iterator().next();
        return first == null ? null : new double[] {first.getLat(), first.getLng()};
    }

    /** 그 종류 후보 중 아직 안 쓴 곳에서 (점수 − 거리 벌점)이 가장 큰 곳. 없으면 null */
    private Place pickBest(Map<String, Place> candidates, Map<String, Double> scores, Set<String> used, String kind, double[] point) {
        Place best = null;
        double bestValue = Double.NEGATIVE_INFINITY;
        for (Place place : candidates.values()) {
            if (place.getId() == null || used.contains(place.getId()) || !kind.equals(place.getKind())) {
                continue;
            }
            double km = point == null ? 0 : Geo.distanceMeters(point[0], point[1], place.getLat(), place.getLng()) / 1000;
            double value = scores.getOrDefault(place.getId(), 0.0) - DISTANCE_PENALTY_PER_KM * km;
            if (value > bestValue) {
                bestValue = value;
                best = place;
            }
        }
        return best;
    }

    /** 여행 첫날부터 몇째 날인지 (첫날 = 1) */
    private int dayOf(String startDate, String date) {
        if (date == null) {
            return -1;
        }
        return (int) ChronoUnit.DAYS.between(LocalDate.parse(startDate), LocalDate.parse(date)) + 1;
    }

    private AiDraftStop toDraftStop(Place place, int day, String time, int stay) {
        AiDraftStop stop = new AiDraftStop();
        stop.setDay(day);
        stop.setId(place.getId());
        stop.setName(place.getName());
        stop.setTime(time);
        stop.setStay(stay);
        stop.setFixed(false);
        return stop;
    }

    private Stop toStayStop(PlanStay stay, int day, int clock) {
        Stop stop = new Stop();
        stop.setDay(day);
        stop.setName(stay.getName());
        stop.setCategory("숙소");
        stop.setAddress(stay.getAddress());
        stop.setLat(stay.getLat());
        stop.setLng(stay.getLng());
        stop.setTime(Geo.toHhmm(clock));
        stop.setStay(STAY_MIN);
        stop.setFixed(false);
        stop.setState("todo");
        stop.setMemo("숙소 · 체크인 " + (stay.getCheckInTime() == null ? "" : stay.getCheckInTime()) + " · 다음 날 여기서 출발");
        return stop;
    }
}
