package com.tripbiseo.plan.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.plan.dto.AiDraftStop;
import com.tripbiseo.plan.dto.FixedVisit;
import com.tripbiseo.plan.dto.PlanArea;
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
 *   11/04 가까운 곳 우선: 걸어서 15분 안이면 가산점, 1.5km 넘으면 더 뺌, 그날 첫 장소 쪽으로 되돌아가면 뺌. AI가 고른 곳도 2km 넘게 멀면 다시 고름
 * 숙소: 체크인 날 마지막 일정으로 넣고, 다음 날 첫 장소는 숙소에서 가까운 곳부터 (addStayStops · dayStartPoint)
 */
@Component
@RequiredArgsConstructor
public class PlanRuleMaker {

    private final PlaceService placeService;               // 영업시간 확인
    private final PlanCandidateFinder planCandidateFinder; // 꼭 갈 곳인지 · 종류 이름

    private static final double DISTANCE_PENALTY_PER_KM = 0.3; // 평점 0.3점 = 1km 더 가는 것과 같게 봄
    private static final int NEAR_WALK_M = 1125;               // 걸어서 15분 (시속 4.5km)
    private static final double NEAR_BONUS = 0.5;              // 그 안이면 더하는 점수
    private static final int FAR_FROM_M = 1500;                // 이보다 멀면
    private static final double FAR_PENALTY_PER_KM = 1.0;      // 넘은 1km마다 더 뺌
    private static final double BACKWARD_PENALTY = 0.5;        // 온 방향으로 되돌아가면 뺌
    private static final int MAX_CAFES_PER_DAY = 2;            // 하루 카페 수
    private static final int AI_PICK_LIMIT_M = 2000;           // AI가 고른 곳이 앞 장소에서 이보다 멀면 다시 고름
    private static final int TODAY_START_AFTER_MIN = 60;       // 오늘 출발이면 지금부터 1시간 뒤 칸부터
    private static final int TODAY_LAST_END_MIN = 23 * 60;     // 오늘 당겨 쓴 칸은 23:00 안에 끝나는 것만
    private static final int SLOT_GAP_MIN = 30;                // 당겨 쓴 칸 앞뒤 이동 여유
    private static final int TIME_STEP_MIN = 10;               // 당긴 시각은 10분 단위로 (17:43 → 17:50)
    private static final int PULL_LATEST_DAY_KIND_MIN = 19 * 60;   // 관광·전시·쇼핑 칸을 당겨 넣을 때 늦어도 이 시각에 시작
    private static final int PULL_LATEST_NIGHT_KIND_MIN = 21 * 60; // 맛집·카페·술집 칸은 이 시각까지
    private static final List<String> NIGHT_KINDS = List.of("food", "cafe", "bar");
    private static final int STAY_MIN = 30;                    // 숙소 칸 머무는 시간 (일정표 표시용)
    private static final int STAY_AFTER_LAST_MIN = 30;         // 마지막 일정이 끝나고 숙소까지 여유
    private static final int LATEST_STAY_MIN = 23 * 60 + 30;   // 숙소 칸은 늦어도 23:30
    private static final String CAFE_KIND = "cafe";
    private static final List<String> DEFAULT_KINDS = List.of("food", CAFE_KIND);
    private static final List<String> ALWAYS_KINDS = List.of("food", CAFE_KIND, "tour"); // 고른 것과 상관없이 넣는 밥·카페·들를 곳
    private static final int LUNCH_START_MIN = 11 * 60 + 30;   // 점심 칸은 이 사이에 시작 (시작·끝 시간을 고른 경우)
    private static final int LUNCH_END_MIN = 13 * 60 + 30;
    private static final int DINNER_START_MIN = 17 * 60 + 30;  // 저녁 칸
    private static final int DINNER_END_MIN = 19 * 60 + 30;
    private static final int BAR_FROM_MIN = 18 * 60;           // 술집 칸은 이때부터
    private static final int TIMED_GAP_MIN = 20;               // 칸 사이 이동 여유
    private static final int MIN_SLOT_MIN = 40;                // 남은 시간이 이보다 적으면 칸을 안 만듦
    private static final String FOOD_KIND = "food";
    private static final int DEFAULT_KEPT_STAY_MIN = 60;       // 담은 곳의 머무는 분을 모를 때
    private static final String BAR_KIND = "bar";
    private static final Map<String, Integer> KIND_STAY_MIN = Map.of(FOOD_KIND, 70, CAFE_KIND, 60, BAR_KIND, 90); // 나머지는 80분
    private static final int OTHER_STAY_MIN = 80;
    private static final int CHOSEN_KIND_EXTRA_MIN = 15;       // "어디 위주로"에서 고른 맛집·카페는 더 오래 (11/06)
    private static final int SHORTEST_STAY_MIN = 40;           // 동행에 맞춰 줄여도 이보다 짧게는 안 함
    private static final int DEFAULT_EXTRA_STAY_MIN = 90;      // 꼭 갈 곳·고정 일정의 머무는 분을 모를 때

    // 하루 칸: "시각/머무는 분/이 칸에 넣을 종류(앞이 먼저)". 고른 종류가 하나도 없는 칸은 건너뜀
    // 칸 사이는 이동 20~40분을 비워 둠 (10:00~11:30 → 12:00 점심)
    private static final List<String> DAY_SLOTS = List.of(
            "10:00/90/tour,culture,shop",
            "12:00/70/food",
            "13:40/60/cafe,culture,tour",
            "15:10/80/culture,tour,shop",
            "16:50/60/shop,tour,culture,cafe", // 카페는 13:40에 이미 있으니 볼거리가 없을 때만
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

    /** 사용자가 고른 종류 그대로 (없으면 맛집·카페) */
    public List<String> chosenKindsOf(PlanConditions conditions) {
        return conditions.getKinds() == null || conditions.getKinds().isEmpty() ? DEFAULT_KINDS : conditions.getKinds();
    }

    /** 일정에 넣을 종류: 고른 종류 + 밥·카페·들를 곳 (고른 건 "위주"라서 끼니와 산책할 곳은 항상 넣음). 빼 달라고 한 종류는 안 넣음 */
    public List<String> kindsOf(PlanConditions conditions) {
        List<String> kinds = new ArrayList<>(chosenKindsOf(conditions));
        for (String kind : ALWAYS_KINDS) {
            boolean isAvoided = conditions.getAvoidKinds() != null && conditions.getAvoidKinds().contains(kind);
            if (!isAvoided && !kinds.contains(kind)) {
                kinds.add(kind);
            }
        }
        return kinds;
    }

    /** 오늘 출발인데 지금 시각으로는 넣을 칸이 하나도 없는지 (밤늦게 만들 때) */
    public boolean isTodayOver(PlanConditions conditions) {
        if (hasDayTime(conditions)) {
            return false; // 사용자가 시작·끝 시간을 직접 골랐으면 그대로
        }
        for (String[] slot : pullToNow(getSlotList(conditions))) {
            if (slot != null) {
                return false;
            }
        }
        return true;
    }

    /** 하루 칸 목록: [넣을 종류들(쉼표), "HH:mm", 머무는 분]. 칸 번호는 순서대로 S1, S2 … */
    public List<String[]> getSlotList(PlanConditions conditions) {
        return getSlotList(conditions, 1);
    }

    /** 그날 칸 목록 (담은 곳이 있으면 그 시간은 비워 둠) */
    private List<String[]> getSlotList(PlanConditions conditions, int day) {
        if (hasDayTime(conditions)) {
            return timedSlotsOf(conditions, Geo.toMinutes(conditions.getStartTime()), Geo.toMinutes(conditions.getEndTime()), keptOfDay(conditions, day));
        }
        boolean isCafeChosen = conditions.getKinds() != null && conditions.getKinds().contains(CAFE_KIND);
        return slotsOf(kindsOf(conditions), isCafeChosen);
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
                if (isUserNamed(pick, conditions, candidates)) {
                    extras.add(pick); // 칸 없이 오는 건 사용자가 말한 곳만 (Gemini가 멋대로 더한 곳은 뺌)
                }
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
            addUsedByName(extra.getName(), candidates, used); // 번호 없이 이름만 온 곳도 칸에 또 안 들어가게
        }
        int replaced = 0;
        List<PlanArea> areas = orderAreas(conditions, startDate, candidates);
        for (int day = 1; day <= days; day++) {
            Integer dayEndMin = day == days ? lastDayEndMin : null; // 끝 장소 마감은 마지막 날만
            replaced += fillDay(conditions, candidates, scores, startDate, day, days, areas, extras, slotPicks, used, stops, dayEndMin, maxWalkMeters);
        }
        if (replaced > 0) {
            warnings.add("AI가 고른 " + replaced + "곳이 종류·영업시간·거리와 맞지 않아 평점·거리로 다시 골랐어요");
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

    /** 시작·끝 시간을 골랐는지 */
    private boolean hasDayTime(PlanConditions conditions) {
        return conditions.getStartTime() != null && conditions.getEndTime() != null;
    }

    /**
     * 고른 시간 안에서 하루 칸 만들기 (11/04): 첫 칸은 시작 시각, 마지막 칸은 끝 시각 안에 끝남.
     * 시계를 앞으로 돌리며, 점심(11:30~13:30)·저녁(17:30~19:30) 때가 되면 식사 칸, 그 밖에는 볼거리·카페 칸
     */
    private List<String[]> timedSlotsOf(PlanConditions conditions, int startMin, int endMin, List<Stop> kept) {
        List<String> kinds = kindsOf(conditions);
        List<String[]> slots = new ArrayList<>();
        boolean hasFood = kinds.contains(FOOD_KIND);
        boolean isLunchDone = !hasFood || hasKeptMeal(kept, LUNCH_START_MIN, LUNCH_END_MIN);
        boolean isDinnerDone = !hasFood || hasKeptMeal(kept, DINNER_START_MIN, DINNER_END_MIN);
        String previous = "";
        int clock = startMin;
        while (endMin - clock >= MIN_SLOT_MIN) {
            Stop nextKept = nextKeptAfter(kept, clock);
            int limit = nextKept == null ? endMin : Geo.toMinutes(nextKept.getTime()) - TIMED_GAP_MIN; // 담은 곳 전에 끝나게
            if (nextKept != null && limit - clock < MIN_SLOT_MIN) {
                clock = roundUp(keptEndOf(nextKept) + TIMED_GAP_MIN); // 사이에 칸이 안 들어가면 담은 곳 뒤로
                previous = kindOfKept(nextKept);
                continue;
            }
            boolean isLunchTime = !isLunchDone && clock >= LUNCH_START_MIN && clock < LUNCH_END_MIN;
            boolean isDinnerTime = !isDinnerDone && clock >= DINNER_START_MIN && clock < DINNER_END_MIN;
            String slotKinds = isLunchTime || isDinnerTime ? FOOD_KIND : outingKindsOf(kinds, clock, previous);
            if (slotKinds.isEmpty()) {
                clock += TIME_STEP_MIN; // 넣을 볼거리가 없으면 다음 식사 때까지 기다림
                continue;
            }
            isLunchDone = isLunchDone || isLunchTime;
            isDinnerDone = isDinnerDone || isDinnerTime;
            int stay = Math.min(stayOfSlot(slotKinds, conditions), limit - clock);
            slots.add(new String[] {slotKinds, Geo.toHhmm(clock), String.valueOf(stay)});
            previous = slotKinds;
            clock = roundUp(clock + stay + TIMED_GAP_MIN);
        }
        return slots;
    }

    /** 그날 담은 곳 (시각 순). 시각이 없는 칸은 뺌 */
    private List<Stop> keptOfDay(PlanConditions conditions, int day) {
        List<Stop> kept = new ArrayList<>();
        if (conditions.getKeptStops() == null) {
            return kept;
        }
        for (Stop stop : conditions.getKeptStops()) {
            boolean isThisDay = (stop.getDay() == null ? 1 : stop.getDay()) == day;
            if (isThisDay && stop.getTime() != null && stop.getTime().matches("\\d{2}:\\d{2}")) {
                kept.add(stop);
            }
        }
        kept.sort((first, second) -> first.getTime().compareTo(second.getTime())); // 시각 글자 순
        return kept;
    }

    /** clock 뒤에 끝나는 첫 담은 곳 (없으면 null) */
    private Stop nextKeptAfter(List<Stop> kept, int clock) {
        for (Stop stop : kept) {
            if (keptEndOf(stop) > clock) {
                return stop;
            }
        }
        return null;
    }

    private int keptEndOf(Stop stop) {
        return Geo.toMinutes(stop.getTime()) + (stop.getStay() == null ? DEFAULT_KEPT_STAY_MIN : stop.getStay());
    }

    /** 이 시간대에 담아 둔 식사가 있는지 */
    private boolean hasKeptMeal(List<Stop> kept, int fromMin, int toMin) {
        for (Stop stop : kept) {
            int start = Geo.toMinutes(stop.getTime());
            if (start >= fromMin && start < toMin && FOOD_KIND.equals(kindOfKept(stop))) {
                return true;
            }
        }
        return false;
    }

    /** 담은 곳의 종류: 우리 DB에 있으면 그 종류, 없으면(카카오에서 찾은 곳) 분류 글자로 */
    private String kindOfKept(Stop stop) {
        if (stop.getPlaceId() != null) {
            try {
                return placeService.getPlace(stop.getPlaceId()).getKind();
            } catch (ApiException e) {
                // 없어진 장소면 아래 분류 글자로
            }
        }
        String category = stop.getCategory() == null ? "" : stop.getCategory();
        if (category.matches(".*(카페|커피|디저트|베이커리).*")) {
            return CAFE_KIND;
        }
        return category.matches(".*(관광|문화|쇼핑|공원|산책|거리).*") ? "tour" : FOOD_KIND;
    }

    /** 식사 때가 아닌 칸의 종류 (앞이 먼저): 밤엔 술집, 식사 뒤엔 카페, 오전엔 볼거리. 카페 다음에 또 카페는 안 넣음 */
    private String outingKindsOf(List<String> kinds, int clock, String previous) {
        if (kinds.contains(BAR_KIND) && clock >= BAR_FROM_MIN && !previous.equals(BAR_KIND)) {
            return BAR_KIND;
        }
        List<String> order = previous.equals(FOOD_KIND) ? List.of(CAFE_KIND, "culture", "tour", "shop") : List.of("tour", "culture", "shop", CAFE_KIND);
        List<String> chosen = new ArrayList<>();
        for (String kind : order) {
            boolean isCafeAgain = kind.equals(CAFE_KIND) && previous.equals(CAFE_KIND);
            if (kinds.contains(kind) && !isCafeAgain) {
                chosen.add(kind);
            }
        }
        return String.join(",", chosen);
    }

    /**
     * 칸의 첫 종류로 머무는 분 (11/06): 기본 분 + "어디 위주로"에서 고른 맛집·카페면 15분 + 누구랑에 따라 더하거나 뺌.
     * 맛집·카페를 골랐다는 건 거기에 시간을 더 쓰고 싶다는 뜻이라서
     */
    private int stayOfSlot(String slotKinds, PlanConditions conditions) {
        String kind = slotKinds.split(",")[0];
        int stay = KIND_STAY_MIN.getOrDefault(kind, OTHER_STAY_MIN);
        boolean isChosen = conditions.getKinds() != null && conditions.getKinds().contains(kind);
        if (isChosen && (FOOD_KIND.equals(kind) || CAFE_KIND.equals(kind))) {
            stay += CHOSEN_KIND_EXTRA_MIN;
        }
        stay += companionExtraMin(conditions.getCompanion(), kind);
        return Math.max(SHORTEST_STAY_MIN, stay);
    }

    /**
     * 누구랑에 따라 더하는 분 (11/06). 워크숍: 회식·카페 길게 / 친구: 밥·카페 조금 길게 / 연인: 카페 길게 /
     * 가족: 밥 조금 길게 / 아이와: 오래 못 머물러서 짧게 / 혼자: 밥·카페 짧게
     */
    private int companionExtraMin(String companion, String kind) {
        boolean isFood = FOOD_KIND.equals(kind) || BAR_KIND.equals(kind);
        boolean isCafe = CAFE_KIND.equals(kind);
        if ("워크숍".equals(companion)) {
            return isFood ? 30 : isCafe ? 20 : 0;
        }
        if ("친구".equals(companion)) {
            return isFood || isCafe ? 10 : 0;
        }
        if ("연인".equals(companion)) {
            return isCafe ? 20 : isFood ? 10 : 0;
        }
        if ("가족".equals(companion)) {
            return isFood ? 10 : 0;
        }
        if ("아이와".equals(companion)) {
            return -10;
        }
        if ("혼자".equals(companion)) {
            return isFood ? -20 : isCafe ? -10 : 0;
        }
        return 0;
    }

    /** 하루 칸 채우기. Gemini가 고른 곳을 규칙으로 바꾼 칸 수를 돌려줌 */
    private int fillDay(PlanConditions conditions, Map<String, Place> candidates, Map<String, Double> scores, String startDate,
                        int day, int days, List<PlanArea> areas, List<AiDraftStop> extras, Map<String, AiDraftStop> slotPicks, Set<String> used, List<AiDraftStop> stops,
                        Integer dayEndMin, Integer maxWalkMeters) {
        LocalDate date = LocalDate.parse(startDate).plusDays(day - 1);
        boolean isToday = date.equals(Geo.now().toLocalDate());
        List<Stop> kept = keptOfDay(conditions, day);
        double[] point = dayStartPoint(conditions, startDate, day, candidates);
        double[] firstPoint = null; // 그날 첫 장소 (하루 동선의 방향을 정해요)
        String lastKind = "";       // 앞 칸 종류 (카페 연달아 안 가게)
        int cafeCount = 0;          // 오늘 넣은 카페 수
        if (!kept.isEmpty() && realStartOf(conditions, startDate, day) == null) {
            point = new double[] {kept.get(0).getLat(), kept.get(0).getLng()}; // 담은 곳 근처부터
        }
        for (Stop stop : kept) {
            cafeCount += CAFE_KIND.equals(kindOfKept(stop)) ? 1 : 0;
        }
        List<String[]> slots = getSlotList(conditions, day);
        if (isToday && !hasDayTime(conditions)) {
            slots = pullToNow(slots); // 오늘 출발이면 지난 칸을 버리지 않고 남은 시간으로 당김 (11/04)
        }
        int replaced = 0;
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i) == null) {
                continue; // 오늘 남은 시간에 못 들어가는 칸
            }
            int start = Geo.toMinutes(slots.get(i)[1]);
            Stop keptBefore = lastKeptBefore(kept, start);
            if (keptBefore != null && (firstPoint == null || isLater(keptBefore, stops, day, start))) {
                point = new double[] {keptBefore.getLat(), keptBefore.getLng()}; // 바로 앞이 담은 곳이면 거기서 가까운 곳
                firstPoint = firstPoint == null ? point : firstPoint;
                lastKind = kindOfKept(keptBefore);
            }
            List<String> slotKinds = withoutExtraCafe(Arrays.asList(slots.get(i)[0].split(",")), lastKind, cafeCount);
            int stay = Integer.parseInt(slots.get(i)[2]);
            boolean isAfterEnd = dayEndMin != null && start + stay > dayEndMin; // 끝 장소로 가야 해서 못 쓰는 칸
            if (isAfterEnd || overlapsExtra(extras, day, start, stay)) {
                continue; // 마감 뒤이거나 꼭 갈 곳·고정 일정과 겹치는 칸
            }
            AiDraftStop pick = slotPicks.get(day + "/S" + (i + 1));
            // 여러 구면 이 칸은 정해진 구의 후보에서 먼저 (없으면 전체)
            Map<String, Place> slotCandidates = candidatesOfSlot(candidates, areas, (day - 1) * slots.size() + i, days * slots.size());
            Place place = pick == null ? null : checkPick(pick, slotKinds, slotCandidates, used, conditions, date, start, stay, point, maxWalkMeters);
            String reason = place == null ? null : pick.getReason();
            if (pick != null && place == null) {
                replaced++;
            }
            for (int k = 0; place == null && k < slotKinds.size(); k++) { // 이 칸에 넣을 종류를 앞에서부터 (걷는 거리 안에서)
                place = pickBest(slotCandidates, scores, used, slotKinds.get(k), point, firstPoint, date, start, stay, maxWalkMeters);
            }
            for (int k = 0; place == null && slotCandidates != candidates && k < slotKinds.size(); k++) { // 그 구에 없으면 다른 구에서
                place = pickBest(candidates, scores, used, slotKinds.get(k), point, firstPoint, date, start, stay, maxWalkMeters);
            }
            for (int k = 0; place == null && maxWalkMeters != null && k < slotKinds.size(); k++) { // 가까운 곳이 없으면 거리 제한 없이
                place = pickBest(candidates, scores, used, slotKinds.get(k), point, firstPoint, date, start, stay, null);
            }
            if (place == null) {
                continue; // 넣을 종류 후보가 다 떨어짐
            }
            used.add(place.getId());
            AiDraftStop stop = toDraftStop(place, day, slots.get(i)[1], stay);
            stop.setReason(reason);
            stops.add(stop);
            point = new double[] {place.getLat(), place.getLng()};
            firstPoint = firstPoint == null ? point : firstPoint;
            lastKind = place.getKind();
            cafeCount += CAFE_KIND.equals(lastKind) ? 1 : 0;
        }
        return replaced;
    }

    /** 후보 중 이름이 같은 곳을 이미 쓴 곳으로 */
    private void addUsedByName(String name, Map<String, Place> candidates, Set<String> used) {
        if (name == null) {
            return;
        }
        for (Place place : candidates.values()) {
            if (place.getId() != null && (place.getName().contains(name) || name.contains(place.getName()))) {
                used.add(place.getId());
            }
        }
    }

    /** 사용자가 말한 곳인지: 이름이 고정 일정·꼭 갈 곳·가능하면 갈 곳에 있음 (Gemini가 붙인 fixed 표시는 믿지 않음) */
    private boolean isUserNamed(AiDraftStop pick, PlanConditions conditions, Map<String, Place> candidates) {
        Place place = pick.getId() == null ? null : candidates.get(pick.getId());
        String name = place != null ? place.getName() : pick.getName();
        return name != null && (isInNames(name, conditions.getMustVisit()) || isInNames(name, conditions.getWishVisit()) || isInFixed(name, conditions));
    }

    private boolean isInFixed(String name, PlanConditions conditions) {
        if (conditions.getFixed() == null) {
            return false;
        }
        for (FixedVisit fixed : conditions.getFixed()) {
            if (fixed.getName() != null && (name.contains(fixed.getName()) || fixed.getName().contains(name))) {
                return true;
            }
        }
        return false;
    }

    /** 이름이 서로 포함되면 같은 곳 */
    private boolean isInNames(String name, List<String> names) {
        if (names == null) {
            return false;
        }
        for (String item : names) {
            if (name.contains(item) || item.contains(name)) {
                return true;
            }
        }
        return false;
    }

    /** 고른 구를 출발지(없으면 첫 구)에서 가까운 순으로 이어 왔다 갔다 하지 않게. 하나 이하면 그대로 */
    private List<PlanArea> orderAreas(PlanConditions conditions, String startDate, Map<String, Place> candidates) {
        List<PlanArea> left = conditions.getAreas() == null ? new ArrayList<>() : new ArrayList<>(conditions.getAreas());
        if (left.size() < 2) {
            return left;
        }
        double[] point = conditions.getOriginLat() != null ? new double[] {conditions.getOriginLat(), conditions.getOriginLng()}
                : new double[] {left.get(0).getLat(), left.get(0).getLng()};
        List<PlanArea> ordered = new ArrayList<>();
        while (!left.isEmpty()) {
            PlanArea nearest = left.get(0);
            for (PlanArea area : left) {
                if (distanceTo(point, area) < distanceTo(point, nearest)) {
                    nearest = area;
                }
            }
            ordered.add(nearest);
            left.remove(nearest);
            point = new double[] {nearest.getLat(), nearest.getLng()};
        }
        return ordered;
    }

    private double distanceTo(double[] point, PlanArea area) {
        return Geo.distanceMeters(point[0], point[1], area.getLat(), area.getLng());
    }

    /** 이 칸의 후보: 칸을 구 수만큼 나눠 차례로 그 구의 장소만. 구가 하나 이하이거나 그 구에 후보가 없으면 전체 */
    private Map<String, Place> candidatesOfSlot(Map<String, Place> candidates, List<PlanArea> areas, int slotIndex, int slotTotal) {
        if (areas.size() < 2) {
            return candidates;
        }
        String name = areas.get(slotIndex * areas.size() / slotTotal).getName();
        Map<String, Place> inArea = new LinkedHashMap<>();
        for (Place place : candidates.values()) {
            if (place.getAddress() != null && place.getAddress().contains(name)) {
                inArea.put(place.getId(), place);
            }
        }
        return inArea.isEmpty() ? candidates : inArea;
    }

    /** Gemini가 고른 곳 검사: 후보 안 · 아직 안 씀 · 칸 종류와 같음(꼭 갈 곳은 예외) · 도착할 때 영업. 어기면 null */
    private Place checkPick(AiDraftStop pick, List<String> slotKinds, Map<String, Place> candidates, Set<String> used,
                            PlanConditions conditions, LocalDate date, int start, int stay, double[] point, Integer maxWalkMeters) {
        Place place = pick.getId() == null ? null : candidates.get(pick.getId()); // 후보 밖 id는 믿지 않음
        if (place == null || used.contains(place.getId()) || isTooFar(point, place, maxWalkMeters)) {
            return null;
        }
        boolean isUserWish = planCandidateFinder.isMustVisit(place, conditions.getMustVisit()) || planCandidateFinder.isMustVisit(place, conditions.getWishVisit());
        if (!isUserWish && isTooFar(point, place, AI_PICK_LIMIT_M)) {
            return null; // 사용자가 말한 곳이 아니면 앞 장소에서 2km 안에서
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
        if (conditions.getKeptStops() != null) {
            for (Stop stop : conditions.getKeptStops()) {
                placedKinds.add(kindOfKept(stop)); // 담은 곳의 종류도 넣은 걸로
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

    /**
     * 오늘 출발하는 날의 칸 (11/04). 예전엔 지난 칸을 그냥 버려서, 오후에 짜면 하루가 비고 숙소만 남았어요
     * (카페만 고르면 11:00·14:00·17:00 칸이 다 지남).
     * 1) 지금 1시간 뒤보다 늦은 칸은 그 시각 그대로 2) 지난 칸은 빈 시간에 순서대로 끼워 넣기
     *    (식사·술집 칸은 뒤에 같은 칸이 남아 있으면 안 넣음, 관광·쇼핑은 19:00·맛집·카페·술집은 21:00까지 시작, 23:00 안에 끝)
     * 못 넣는 칸은 null. 칸 번호(S1·S2…)는 그대로라 Gemini가 고른 칸과 짝이 맞아요
     */
    private List<String[]> pullToNow(List<String[]> slots) {
        int from = roundUp(Geo.nowMinutes() + TODAY_START_AFTER_MIN);
        List<String[]> pulled = new ArrayList<>();
        Set<String> laterKinds = new HashSet<>(); // 아직 안 지난 칸의 종류
        for (String[] slot : slots) {
            boolean isLater = Geo.toMinutes(slot[1]) >= from;
            pulled.add(isLater ? slot : null);
            if (isLater) {
                laterKinds.addAll(Arrays.asList(slot[0].split(",")));
            }
        }
        for (int i = 0; i < slots.size(); i++) {
            String[] slot = slots.get(i);
            if (pulled.get(i) != null || isRepeatedMeal(slot, laterKinds)) {
                continue;
            }
            int stay = Integer.parseInt(slot[2]);
            int start = findFreeStart(pulled, from, stay);
            if (start <= latestPullStart(slot[0]) && start + stay <= TODAY_LAST_END_MIN) {
                pulled.set(i, new String[] {slot[0], Geo.toHhmm(start), slot[2]});
            }
        }
        return pulled;
    }

    /** 지난 식사·술집 칸인데 뒤에 같은 칸이 남아 있는지 (점심을 저녁 앞에 또 넣지 않게) */
    private boolean isRepeatedMeal(String[] slot, Set<String> laterKinds) {
        boolean isMeal = slot[0].equals("food") || slot[0].equals("bar");
        return isMeal && laterKinds.contains(slot[0]);
    }

    /** from 뒤에서, 이미 넣은 칸과 앞뒤 30분 여유를 두고 stay분이 들어가는 가장 이른 시작 시각 */
    private int findFreeStart(List<String[]> pulled, int from, int stay) {
        List<int[]> placed = new ArrayList<>(); // [시작, 끝] (분)
        for (String[] slot : pulled) {
            if (slot != null) {
                int start = Geo.toMinutes(slot[1]);
                placed.add(new int[] {start, start + Integer.parseInt(slot[2])});
            }
        }
        placed.sort((a, b) -> a[0] - b[0]); // 시작 시각 순으로 (짧은 람다: 두 칸의 시작을 비교)
        int start = from;
        for (int[] time : placed) {
            if (start + stay + SLOT_GAP_MIN <= time[0]) {
                break; // 이 칸 앞에 들어감
            }
            start = Math.max(start, roundUp(time[1] + SLOT_GAP_MIN));
        }
        return start;
    }

    /** 당겨 넣을 때 늦어도 시작할 시각 (맛집·카페·술집이 들어간 칸은 저녁까지, 관광·전시·쇼핑은 이르게) */
    private int latestPullStart(String kinds) {
        for (String kind : kinds.split(",")) {
            if (NIGHT_KINDS.contains(kind)) {
                return PULL_LATEST_NIGHT_KIND_MIN;
            }
        }
        return PULL_LATEST_DAY_KIND_MIN;
    }

    /** 분을 10분 단위로 올림 (17:43 → 17:50) */
    private int roundUp(int minutes) {
        return (minutes + TIME_STEP_MIN - 1) / TIME_STEP_MIN * TIME_STEP_MIN;
    }

    /** 하루 칸 목록: [넣을 종류들(쉼표), "HH:mm", 머무는 분]. 고른 종류만 남기고, 하나도 없는 칸은 뺌 */
    private List<String[]> slotsOf(List<String> kinds, boolean isCafeChosen) {
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
            narrowCafe(chosen, isCafeChosen);
            boolean isCafeAgain = !slots.isEmpty() && chosen.equals(List.of(CAFE_KIND)) && CAFE_KIND.equals(slots.get(slots.size() - 1)[0]);
            if (!chosen.isEmpty() && !isCafeAgain) { // 카페 다음 칸에 또 카페는 넣지 않음
                slots.add(new String[] {String.join(",", chosen), parts[0], parts[1]});
            }
        }
        return slots;
    }

    /**
     * 카페가 먼저인 칸(점심 뒤)은 카페만. 다른 칸은 다른 종류가 있으면 카페를 뺌.
     * 다만 사용자가 카페를 골랐으면 늦은 오후 칸에도 카페를 먼저 넣음 (사이에 들를 곳이 있어 연달아 가지 않음)
     */
    private void narrowCafe(List<String> chosen, boolean isCafeChosen) {
        if (!chosen.contains(CAFE_KIND) || chosen.size() == 1) {
            return;
        }
        if (CAFE_KIND.equals(chosen.get(0)) || isCafeChosen) {
            chosen.clear();
            chosen.add(CAFE_KIND);
        } else {
            chosen.remove(CAFE_KIND);
        }
    }

    /** 사용자가 정한 그날 출발점: 1일차는 출발지, 그 뒤는 전날 숙소. 없으면 null */
    public double[] realStartOf(PlanConditions conditions, String startDate, int day) {
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
        return null;
    }

    /** 그날 첫 출발점: 1일차는 출발지, 그 뒤는 전날 숙소, 없으면 지역 중심 → 첫 후보 */
    private double[] dayStartPoint(PlanConditions conditions, String startDate, int day, Map<String, Place> candidates) {
        double[] start = realStartOf(conditions, startDate, day);
        if (start != null) {
            return start;
        }
        if (conditions.getAreaLat() != null) {
            return new double[] {conditions.getAreaLat(), conditions.getAreaLng()};
        }
        Place first = candidates.isEmpty() ? null : candidates.values().iterator().next();
        return first == null ? null : new double[] {first.getLat(), first.getLng()};
    }

    /** 그 종류 후보 중 아직 안 쓰고 그 시각에 영업하는 곳에서 (점수 − 거리 벌점)이 가장 큰 곳. 없으면 null */
    private Place pickBest(Map<String, Place> candidates, Map<String, Double> scores, Set<String> used, String kind, double[] point, double[] firstPoint,
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
            double value = scores.getOrDefault(place.getId(), 0.0) - routePenaltyOf(place, point, firstPoint);
            if (value > bestValue) {
                bestValue = value;
                best = place;
            }
        }
        return best;
    }

    /** start 전에 시작한 마지막 담은 곳 (없으면 null) */
    private Stop lastKeptBefore(List<Stop> kept, int start) {
        Stop last = null;
        for (Stop stop : kept) {
            if (Geo.toMinutes(stop.getTime()) < start) {
                last = stop;
            }
        }
        return last;
    }

    /** 담은 곳이 AI가 그날 넣은 마지막 칸보다 뒤인지 (그러면 다음 칸의 앞 장소는 담은 곳) */
    private boolean isLater(Stop kept, List<AiDraftStop> stops, int day, int start) {
        int lastPicked = -1;
        for (AiDraftStop stop : stops) {
            if (dayOf(stop) == day && stop.getTime() != null && stop.getTime().matches("\\d{2}:\\d{2}") && Geo.toMinutes(stop.getTime()) < start) {
                lastPicked = Math.max(lastPicked, Geo.toMinutes(stop.getTime()));
            }
        }
        return Geo.toMinutes(kept.getTime()) > lastPicked;
    }

    /** 앞 칸이 카페이거나 오늘 카페를 2곳 넣었으면 이 칸 종류에서 카페를 뺌 */
    private List<String> withoutExtraCafe(List<String> kinds, String lastKind, int cafeCount) {
        if (!CAFE_KIND.equals(lastKind) && cafeCount < MAX_CAFES_PER_DAY) {
            return kinds;
        }
        List<String> rest = new ArrayList<>(kinds);
        rest.remove(CAFE_KIND);
        return rest;
    }

    /** 동선 벌점 (작을수록 좋음): 거리 + 멀면 더 + 가까우면 덜 + 되돌아가면 더 */
    private double routePenaltyOf(Place place, double[] point, double[] firstPoint) {
        if (point == null) {
            return 0;
        }
        double meters = Geo.distanceMeters(point[0], point[1], place.getLat(), place.getLng());
        double penalty = DISTANCE_PENALTY_PER_KM * meters / 1000;
        if (meters <= NEAR_WALK_M) {
            penalty -= NEAR_BONUS;
        }
        if (meters > FAR_FROM_M) {
            penalty += FAR_PENALTY_PER_KM * (meters - FAR_FROM_M) / 1000;
        }
        if (isBackward(place, point, firstPoint)) {
            penalty += BACKWARD_PENALTY;
        }
        return penalty;
    }

    /** 그날 첫 장소 → 앞 장소 방향과 반대로 가는지 (두 방향의 내적이 음수면 반대. 경도는 위도에 맞춰 줄임) */
    private boolean isBackward(Place place, double[] point, double[] firstPoint) {
        if (firstPoint == null || firstPoint == point) {
            return false; // 아직 방향이 안 정해짐
        }
        double lngScale = Math.cos(Math.toRadians(point[0]));
        double wentLat = point[0] - firstPoint[0];
        double wentLng = (point[1] - firstPoint[1]) * lngScale;
        double goLat = place.getLat() - point[0];
        double goLng = (place.getLng() - point[1]) * lngScale;
        return wentLat * goLat + wentLng * goLng < 0;
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
