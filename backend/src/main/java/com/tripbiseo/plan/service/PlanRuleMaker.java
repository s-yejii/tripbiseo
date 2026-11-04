package com.tripbiseo.plan.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
import com.tripbiseo.trip.dto.OpenInfo;
import com.tripbiseo.trip.dto.Place;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.service.PlaceService;

import lombok.RequiredArgsConstructor;

/**
 * ③ 하루 칸 틀과 칸 채우기 (11/03). 고른 칩(종류)은 서버가 지키고, Gemini는 칸 안에서 고르기·이유만 맡아요.
 *   하루 틀: 정해 둔 칸(S1 10:00 관광·전시·쇼핑, S2 12:00 점심, S3 13:40 카페 …) 중 고른 종류가 있는 칸만
 *   칸 채우기: Gemini가 고른 곳이 조건(후보 안·칸 종류·안 겹침·도착 때 영업)을 지키면 그대로, 어기면 그 칸만 규칙으로
 *   규칙 = 칸 종류 후보 중 "점수 − 거리"가 가장 큰 곳 (영업 안 하는 곳 빼고)
 *   점수 = 보정 평점 + 고른 태그 가산점 (PlanCandidateFinder.scoreOf), 거리는 앞 장소(첫 장소는 출발지·숙소)에서 1km마다 0.3점 뺌
 * 숙소: 체크인 날 마지막 일정으로 넣고, 다음 날 첫 장소는 숙소에서 가까운 곳부터 (addStayStops · dayStartPoint)
 */
@Component
@RequiredArgsConstructor
public class PlanRuleMaker {

    private final PlaceService placeService;               // 영업시간 확인
    private final PlanCandidateFinder planCandidateFinder; // 꼭 갈 곳인지 · 종류 이름

    private static final double DISTANCE_PENALTY_PER_KM = 0.3; // 평점 0.3점 = 1km 더 가는 것과 같게 봄
    private static final int TODAY_START_AFTER_MIN = 60;       // 오늘 출발이면 지금부터 1시간 뒤 칸부터
    private static final int STAY_MIN = 30;                    // 숙소 칸 머무는 시간 (일정표 표시용)
    private static final int STAY_AFTER_LAST_MIN = 30;         // 마지막 일정이 끝나고 숙소까지 여유
    private static final int LATEST_STAY_MIN = 23 * 60 + 30;   // 숙소 칸은 늦어도 23:30
    private static final List<String> DEFAULT_KINDS = List.of("food", "cafe");
    private static final int DEFAULT_EXTRA_STAY_MIN = 90;      // 꼭 갈 곳·고정 일정의 머무는 분을 모를 때

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

    /** 하루 칸 목록: [넣을 종류들(쉼표), "HH:mm", 머무는 분]. 칸 번호는 순서대로 S1, S2 … */
    public List<String[]> getSlotList(PlanConditions conditions) {
        return slotsOf(kindsOf(conditions));
    }

    /**
     * 날마다 칸 채우기. picks = Gemini가 고른 것 (Gemini가 안 되면 꼭 갈 곳·고정 일정만).
     * 칸 없이 온 것(꼭 갈 곳·시간 고정)은 그 시각 그대로 두고, 그 시간과 겹치는 칸은 비움.
     * 칸마다 Gemini가 고른 곳이 조건을 어기면 그 칸만 규칙으로 다시 고르고, 바꾼 수·못 채운 종류는 warnings에 적음
     * 11/04: lastDayEndMin(끝 장소로 가야 해서 마지막 날 일정이 끝나야 하는 시각, 없으면 null) 뒤 칸은 비움,
     *        maxWalkMeters(한 번에 걷는 거리 상한, 없으면 null)보다 먼 곳은 가까운 곳이 하나도 없을 때만 고름
     */
    public List<AiDraftStop> fillSlots(PlanConditions conditions, Map<String, Place> candidates, Map<String, Double> scores,
                                       int days, String startDate, List<AiDraftStop> picks, List<String> warnings,
                                       Integer lastDayEndMin, Integer maxWalkMeters) {
        List<AiDraftStop> extras = new ArrayList<>();
        Map<String, AiDraftStop> slotPicks = new HashMap<>(); // "1/S2"(날/칸) → Gemini가 고른 것
        for (AiDraftStop pick : picks) {
            if (pick.getSlot() == null || pick.getSlot().isBlank()) {
                extras.add(pick);
            } else {
                slotPicks.put(dayOf(pick) + "/" + pick.getSlot().trim().toUpperCase(), pick);
            }
        }
        List<AiDraftStop> stops = new ArrayList<>(extras);
        Set<String> used = new HashSet<>();
        for (AiDraftStop extra : extras) {
            if (extra.getId() != null) {
                used.add(extra.getId());
            }
        }
        int replaced = 0;
        for (int day = 1; day <= days; day++) {
            Integer dayEndMin = day == days ? lastDayEndMin : null; // 끝 장소 마감은 마지막 날만
            replaced += fillDay(conditions, candidates, scores, startDate, day, extras, slotPicks, used, stops, dayEndMin, maxWalkMeters);
        }
        if (replaced > 0) {
            warnings.add("AI가 고른 " + replaced + "곳이 고른 종류·영업시간과 맞지 않아 평점·거리로 다시 골랐어요");
        }
        addMissingKindWarnings(conditions, candidates, stops, warnings);
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

    /** 하루 칸 채우기. Gemini가 고른 곳을 규칙으로 바꾼 칸 수를 돌려줌 */
    private int fillDay(PlanConditions conditions, Map<String, Place> candidates, Map<String, Double> scores, String startDate,
                        int day, List<AiDraftStop> extras, Map<String, AiDraftStop> slotPicks, Set<String> used, List<AiDraftStop> stops,
                        Integer dayEndMin, Integer maxWalkMeters) {
        LocalDate date = LocalDate.parse(startDate).plusDays(day - 1);
        boolean isToday = date.equals(Geo.now().toLocalDate());
        double[] point = dayStartPoint(conditions, startDate, day, candidates);
        List<String[]> slots = getSlotList(conditions);
        int replaced = 0;
        for (int i = 0; i < slots.size(); i++) {
            List<String> slotKinds = Arrays.asList(slots.get(i)[0].split(","));
            int start = Geo.toMinutes(slots.get(i)[1]);
            int stay = Integer.parseInt(slots.get(i)[2]);
            boolean isPast = isToday && start < Geo.nowMinutes() + TODAY_START_AFTER_MIN;
            boolean isAfterEnd = dayEndMin != null && start + stay > dayEndMin; // 끝 장소로 가야 해서 못 쓰는 칸
            if (isPast || isAfterEnd || overlapsExtra(extras, day, start, stay)) {
                continue; // 이미 지난 시각이거나 마감 뒤이거나 꼭 갈 곳·고정 일정과 겹치는 칸
            }
            AiDraftStop pick = slotPicks.get(day + "/S" + (i + 1));
            Place place = pick == null ? null : checkPick(pick, slotKinds, candidates, used, conditions, date, start, stay, point, maxWalkMeters);
            String reason = place == null ? null : pick.getReason();
            if (pick != null && place == null) {
                replaced++;
            }
            for (int k = 0; place == null && k < slotKinds.size(); k++) { // 이 칸에 넣을 종류를 앞에서부터 (걷는 거리 안에서)
                place = pickBest(candidates, scores, used, slotKinds.get(k), point, date, start, stay, maxWalkMeters);
            }
            for (int k = 0; place == null && maxWalkMeters != null && k < slotKinds.size(); k++) { // 가까운 곳이 없으면 거리 제한 없이
                place = pickBest(candidates, scores, used, slotKinds.get(k), point, date, start, stay, null);
            }
            if (place == null) {
                continue; // 넣을 종류 후보가 다 떨어짐
            }
            used.add(place.getId());
            AiDraftStop stop = toDraftStop(place, day, slots.get(i)[1], stay);
            stop.setReason(reason);
            stops.add(stop);
            point = new double[] {place.getLat(), place.getLng()};
        }
        return replaced;
    }

    /** Gemini가 고른 곳 검사: 후보 안 · 아직 안 씀 · 칸 종류와 같음(꼭 갈 곳은 예외) · 도착할 때 영업. 어기면 null */
    private Place checkPick(AiDraftStop pick, List<String> slotKinds, Map<String, Place> candidates, Set<String> used,
                            PlanConditions conditions, LocalDate date, int start, int stay, double[] point, Integer maxWalkMeters) {
        Place place = pick.getId() == null ? null : candidates.get(pick.getId()); // 후보 밖 id는 믿지 않음
        if (place == null || used.contains(place.getId()) || isTooFar(point, place, maxWalkMeters)) {
            return null;
        }
        // 꼭 갈 곳·가능하면 갈 곳은 칸 종류와 달라도 됨 (사용자가 직접 말한 곳)
        boolean isKindOk = slotKinds.contains(place.getKind()) || planCandidateFinder.isMustVisit(place, conditions.getMustVisit())
                || planCandidateFinder.isMustVisit(place, conditions.getWishVisit());
        return isKindOk && isOpenFor(place, date, start, stay) ? place : null;
    }

    /** 도착 시각에 열려 있고 머무는 동안 안 닫는지. 영업시간 정보가 없으면 통과 (모르는 걸 틀렸다고 하지 않음) */
    private boolean isOpenFor(Place place, LocalDate date, int start, int stay) {
        OpenInfo open = placeService.getOpenInfo(place, date, start, stay);
        if (Boolean.FALSE.equals(open.getOpen())) {
            return false;
        }
        return open.getMinutesLeft() == null || open.getMinutesLeft() >= stay;
    }

    /** 그날 꼭 갈 곳·고정 일정과 시간이 겹치는지 (칸 [start, start+stay) 와 일정 [시각, 시각+머무는 분)) */
    private boolean overlapsExtra(List<AiDraftStop> extras, int day, int start, int stay) {
        for (AiDraftStop extra : extras) {
            if (dayOf(extra) != day || extra.getTime() == null || !extra.getTime().matches("\\d{2}:\\d{2}")) {
                continue;
            }
            int extraStart = Geo.toMinutes(extra.getTime());
            int extraEnd = extraStart + (extra.getStay() == null ? DEFAULT_EXTRA_STAY_MIN : extra.getStay());
            if (start < extraEnd && extraStart < start + stay) {
                return true;
            }
        }
        return false;
    }

    /** 고른 종류인데 일정에 한 곳도 못 넣었으면 안내 ("근처에 전시·문화 장소가 없어요") */
    private void addMissingKindWarnings(PlanConditions conditions, Map<String, Place> candidates, List<AiDraftStop> stops, List<String> warnings) {
        Set<String> placedKinds = new HashSet<>();
        for (AiDraftStop stop : stops) {
            Place place = stop.getId() == null ? null : candidates.get(stop.getId());
            if (place != null) {
                placedKinds.add(place.getKind());
            }
        }
        for (String kind : kindsOf(conditions)) {
            if (placedKinds.contains(kind)) {
                continue;
            }
            String name = planCandidateFinder.kindName(kind);
            warnings.add(hasKind(candidates, kind) ? "일정 시간이 모자라거나 영업시간이 안 맞아 " + name + " 장소를 못 넣었어요"
                                                   : "고른 지역 근처에서 " + name + " 장소를 찾지 못해 일정에 못 넣었어요");
        }
    }

    /** 후보에 그 종류가 한 곳이라도 있는지 */
    private boolean hasKind(Map<String, Place> candidates, String kind) {
        for (Place place : candidates.values()) {
            if (kind.equals(place.getKind())) {
                return true;
            }
        }
        return false;
    }

    private int dayOf(AiDraftStop stop) {
        return stop.getDay() == null ? 1 : stop.getDay();
    }

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

    /** 그 종류 후보 중 아직 안 쓰고 그 시각에 영업하는 곳에서 (점수 − 거리 벌점)이 가장 큰 곳. 없으면 null */
    private Place pickBest(Map<String, Place> candidates, Map<String, Double> scores, Set<String> used, String kind, double[] point,
                           LocalDate date, int start, int stay, Integer maxMeters) {
        Place best = null;
        double bestValue = Double.NEGATIVE_INFINITY;
        for (Place place : candidates.values()) {
            if (place.getId() == null || used.contains(place.getId()) || !kind.equals(place.getKind()) || isTooFar(point, place, maxMeters)) {
                continue;
            }
            if (!isOpenFor(place, date, start, stay)) {
                continue; // 도착할 때 문 닫았거나 휴무
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

    /** 앞 장소에서 걷는 거리 상한보다 먼지 (상한이 없거나 앞 장소를 모르면 false) */
    private boolean isTooFar(double[] point, Place place, Integer maxMeters) {
        if (maxMeters == null || point == null) {
            return false;
        }
        return Geo.distanceMeters(point[0], point[1], place.getLat(), place.getLng()) > maxMeters;
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
