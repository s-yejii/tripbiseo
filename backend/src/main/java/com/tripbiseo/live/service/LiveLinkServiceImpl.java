package com.tripbiseo.live.service;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.live.dto.AltStop;
import com.tripbiseo.live.dto.Alternative;
import com.tripbiseo.live.dto.LinkFitRequest;
import com.tripbiseo.live.dto.LinkFitResponse;
import com.tripbiseo.trip.dto.OpenInfo;
import com.tripbiseo.trip.dto.Place;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;
import com.tripbiseo.trip.service.PlaceService;

import lombok.RequiredArgsConstructor;

/**
 * 여행 중 링크 추가: 고른 장소를 하나씩, 칸 사이마다 넣어 보고 방해가 가장 적은 자리에 넣음.
 * 시간·영업 계산은 전부 서버(AlternativeCalculator)가 하고 Gemini는 쓰지 않아요. 저장은 기존 /live/apply가 해요.
 * 늦어지거나 식당·카페가 겹치면: 바꾸기 / 둘 다 가고 시간 줄이기 / 한 곳 빼기를 만들어 사용자가 고르게 함.
 */
@Service
@RequiredArgsConstructor
public class LiveLinkServiceImpl implements LiveLinkService {

    private static final String KIND_FOOD = "food";
    private static final String KIND_CAFE = "cafe";
    private static final String KIND_OTHER = "other";
    private static final String CAFE_WORDS = ".*(카페|커피|디저트|베이커리|제과|빵).*";
    private static final String FOOD_WORDS = ".*(음식|식당|맛집|한식|일식|중식|양식|분식|고기|구이|국밥|라멘|라면|우동|소바|국수|냉면|파스타|피자"
            + "|버거|치킨|족발|보쌈|곱창|초밥|스시|돈가스|찌개|탕|뷔페|오리|주꾸미|쭈꾸미|백숙|요리|이자카야|퓨전|멕시칸|양꼬치).*";

    private static final int MAX_PLACES = 5;            // 한 번에 넣을 수 있는 곳
    private static final int CAFE_STAY_MIN = 40;        // 새 칸 머묾 (API.md live/insert와 같음)
    private static final int DEFAULT_STAY_MIN = 60;
    private static final int MEAL_MIN_STAY = 40;        // 시간을 줄여도 식사는 이만큼
    private static final int CAFE_MIN_STAY = 20;
    private static final int OTHER_MIN_STAY = 30;
    private static final int BOTH_MEAL_STAY = 40;       // 식당 둘 다 갈 때 한 곳에 머물 시간
    private static final int BOTH_CAFE_STAY = 25;
    private static final int TRIM_BUFFER_MIN = 5;       // 늦는 시간보다 이만큼 더 줄임
    private static final int PUSH_LIMIT_MIN = 30;       // 고정 일정이 없을 때 이만큼 넘게 밀리면 "늦어진다"로 봄
    private static final int OVERLAP_MIN = 120;         // 같은 종류가 이 시간 안에 있으면 겹친다고 봄
    private static final int ANY_TIME_GAP = 48 * 60;    // 늦어질 때는 시간이 멀어도 같은 종류면 바꾸기를 제안
    private static final int LAST_ARRIVE_MIN = 22 * 60; // 이보다 늦게 도착하는 자리는 피함
    private static final int MINUTES_OF_DAY = 24 * 60;
    // 자리 점수에 더하는 값 (작을수록 좋은 자리)
    private static final int LATE_PENALTY = 10;         // 고정 일정에 1분 늦을 때마다
    private static final int CLOSED_PENALTY = 120;      // 문 닫은 곳이 하나 늘 때마다
    private static final int NEXT_TO_SAME_PENALTY = 45; // 식당 바로 다음에 또 식당
    private static final int TOO_LATE_PENALTY = 120;    // 너무 늦은 도착
    private static final String IMPORTANCE_MUST = "must";
    private static final String IMPORTANCE_OPTIONAL = "drop";
    private static final String[] KEYS = {"A", "B", "C", "D"};

    private final LiveTripReader liveTripReader;
    private final LiveStopFinder liveStopFinder;
    private final AlternativeCalculator alternativeCalculator;
    private final PlaceService placeService;

    /** 계산에 같이 쓰는 재료 (메서드마다 값을 여러 개 넘기지 않게 묶음) */
    private record Context(Trip trip, Stop current, List<Stop> remaining, List<Stop> newStops, boolean hasFixed,
                           Alternative base, int baseClosed, double lat, double lng) {}

    /** 자리 찾기 → 바로 넣어도 되는지 판단 → 필요하면 고를 방법 만들기 */
    @Override
    public LinkFitResponse fitPlace(long memberId, long tripId, LinkFitRequest request) {
        Trip trip = liveTripReader.getOngoingTrip(memberId, tripId, request.getLat(), request.getLng());
        List<Stop> todayStops = liveStopFinder.getTodayStops(trip);
        Stop current = liveStopFinder.findCurrentStop(todayStops, request.getLat(), request.getLng());
        List<Stop> remaining = getRemainingStops(todayStops, current);
        List<Stop> newStops = toNewStops(request, remaining, trip);

        Stop fixedStop = liveStopFinder.findFixedStop(remaining, null);
        Alternative base = remaining.isEmpty() ? null : alternativeCalculator.calculate(trip, current, remaining, request.getLat(), request.getLng());
        int baseClosed = base == null ? 0 : fillOpenInfo(base);
        Context context = new Context(trip, current, remaining, newStops, fixedStop != null, base, baseClosed, request.getLat(), request.getLng());

        Alternative best = calculatePlan(context, placeAll(context, remaining));
        best.setShortName("그대로 넣기");
        best.setTitle("그대로 넣기");
        best.setReason("다른 곳은 그대로 둬요.");
        best.setChanges(newStops.size());

        boolean fits = isFit(context, best);
        Map<Stop, Stop> swaps = findOverlaps(context, best, fits ? OVERLAP_MIN : ANY_TIME_GAP);

        LinkFitResponse response = new LinkFitResponse();
        response.setFits(fits);
        response.setFixedName(fixedStop == null ? null : fixedStop.getName());
        response.setBest(best);
        response.setMessage(getMessage(fits, swaps, best, fixedStop));
        if (!fits || !swaps.isEmpty()) {
            response.setAlternatives(makeOptions(context, best, swaps));
        }
        return response;
    }

    // ---------------------------------------------------------------- 자리 찾기

    /** 새 장소를 하나씩, 그때까지 만든 목록의 칸 사이마다 넣어 보고 점수가 가장 좋은 자리에 넣음 */
    private List<Stop> placeAll(Context context, List<Stop> startStops) {
        List<Stop> stops = new ArrayList<>(startStops);
        for (Stop newStop : context.newStops()) {
            if (!containsName(stops, newStop.getName())) {
                stops = placeOne(context, stops, newStop);
            }
        }
        return stops;
    }

    private List<Stop> placeOne(Context context, List<Stop> stops, Stop newStop) {
        List<Stop> bestStops = null;
        int bestScore = Integer.MAX_VALUE;
        for (int i = 0; i <= stops.size(); i++) {
            List<Stop> tried = new ArrayList<>(stops);
            tried.add(i, newStop);
            int score = scorePlan(context, calculatePlan(context, tried), tried);
            if (score < bestScore) {
                bestScore = score;
                bestStops = tried;
            }
        }
        return bestStops;
    }

    /** 방해 점수: 원래 칸이 밀린 분 + 새 장소까지 이동 + 늦음·문 닫음·겹침·너무 늦은 도착 벌점 */
    private int scorePlan(Context context, Alternative plan, List<Stop> stops) {
        int score = 0;
        for (int pushed : getPushedList(context, plan)) {
            score += pushed;
        }
        for (AltStop stop : plan.getStops()) {
            if (stop.getId() == null && stop.getMoveMin() != null) {
                score += stop.getMoveMin();
            }
        }
        if (context.hasFixed() && plan.getSlackMin() < 0) {
            score += -plan.getSlackMin() * LATE_PENALTY;
        }
        score += Math.max(0, plan.getClosedCount() - context.baseClosed()) * CLOSED_PENALTY;
        score += countSameKindNeighbors(stops) * NEXT_TO_SAME_PENALTY;
        if (getLastArrive(plan) > LAST_ARRIVE_MIN) {
            score += TOO_LATE_PENALTY + getLastArrive(plan) - LAST_ARRIVE_MIN;
        }
        return score;
    }

    /**
     * 장소 이름 → 도착 시각(분). 도착 글자("00:40")는 자정을 넘으면 작아져서, 앞 칸보다 작아지면 하루를 더함.
     * 새 칸은 id가 없어서 이름으로 찾음 (일정 안에 같은 이름은 들어가지 않게 막아 둠). 고정 일정은 계획 시각 그대로
     */
    private Map<String, Integer> getArriveMap(Alternative plan) {
        Map<String, Integer> arriveMap = new HashMap<>();
        int before = -1;
        int addDay = 0;
        for (AltStop stop : plan.getStops()) {
            int minutes = Geo.toMinutes(stop.getEta());
            if (Boolean.TRUE.equals(stop.getFixed())) {
                arriveMap.put(stop.getName(), minutes);
                continue;
            }
            if (before >= 0 && minutes + addDay < before) {
                addDay += MINUTES_OF_DAY;
            }
            before = minutes + addDay;
            arriveMap.put(stop.getName(), before);
        }
        return arriveMap;
    }

    /** 마지막으로 도착하는 시각(분, 자정을 넘으면 1440 이상) */
    private int getLastArrive(Alternative plan) {
        int last = 0;
        for (int arrive : getArriveMap(plan).values()) {
            last = Math.max(last, arrive);
        }
        return last;
    }

    /** 원래 칸마다 밀린 분 (앞당겨진 칸은 0, 고정 일정은 안 셈) */
    private List<Integer> getPushedList(Context context, Alternative plan) {
        List<Integer> pushedList = new ArrayList<>();
        if (context.base() == null) {
            return pushedList;
        }
        Map<String, Integer> arriveMap = getArriveMap(plan);
        Map<String, Integer> baseMap = getArriveMap(context.base());
        for (AltStop stop : plan.getStops()) {
            boolean isOriginal = stop.getId() != null && baseMap.containsKey(stop.getName());
            if (isOriginal && !Boolean.TRUE.equals(stop.getFixed())) {
                pushedList.add(Math.max(0, arriveMap.get(stop.getName()) - baseMap.get(stop.getName())));
            }
        }
        return pushedList;
    }

    /** 새 장소가 낀 이웃 칸 중 같은 종류(식당·카페)인 쌍의 수 */
    private int countSameKindNeighbors(List<Stop> stops) {
        int count = 0;
        for (int i = 0; i + 1 < stops.size(); i++) {
            Stop one = stops.get(i);
            Stop next = stops.get(i + 1);
            boolean hasNew = one.getId() == null || next.getId() == null;
            String kind = toKind(one.getCategory());
            if (hasNew && !KIND_OTHER.equals(kind) && kind.equals(toKind(next.getCategory()))) {
                count++;
            }
        }
        return count;
    }

    /** 다른 칸을 안 바꾸고 넣어도 되나: 문 닫은 곳이 안 늘고, 밤 10시 안에 끝나고, 고정 일정에 안 늦고, 고정 일정이 없으면 30분 넘게 안 밀림 */
    private boolean isFit(Context context, Alternative best) {
        if (best.getClosedCount() > context.baseClosed() || getLastArrive(best) > LAST_ARRIVE_MIN) {
            return false;
        }
        if (context.hasFixed()) {
            return best.getSlackMin() >= 0;
        }
        for (int pushed : getPushedList(context, best)) {
            if (pushed > PUSH_LIMIT_MIN) {
                return false;
            }
        }
        return true;
    }

    /** 새 장소마다 maxGap분 안의 같은 종류 칸(식당·카페, 바꿀 수 있는 칸, 한 칸은 한 번만)을 찾음. 새 칸 → 바꿀 칸 */
    private Map<Stop, Stop> findOverlaps(Context context, Alternative best, int maxGap) {
        Map<Stop, Stop> swaps = new LinkedHashMap<>(); // 넣은 순서를 지키는 Map
        Map<String, Integer> arriveMap = getArriveMap(best);
        for (Stop newStop : context.newStops()) {
            String kind = toKind(newStop.getCategory());
            Stop found = null;
            int foundGap = maxGap + 1;
            for (Stop stop : context.remaining()) {
                boolean isCandidate = !KIND_OTHER.equals(kind) && kind.equals(toKind(stop.getCategory()))
                        && isChangeable(stop) && !swaps.containsValue(stop);
                if (isCandidate) {
                    int gap = Math.abs(arriveMap.get(stop.getName()) - arriveMap.get(newStop.getName()));
                    if (gap < foundGap) {
                        found = stop;
                        foundGap = gap;
                    }
                }
            }
            if (found != null) {
                swaps.put(newStop, found);
            }
        }
        return swaps;
    }

    // ---------------------------------------------------------------- 고를 방법

    /** 겹치면 바꾸기·둘 다 가기, 늦으면 줄이기·빼기. 맨 앞이 추천, 그대로 넣기는 맨 뒤에 */
    private List<Alternative> makeOptions(Context context, Alternative best, Map<Stop, Stop> swaps) {
        List<Alternative> options = new ArrayList<>();
        if (!swaps.isEmpty()) {
            options.add(makeSwapOption(context, swaps));
        }
        Alternative trim = makeTrimOption(context, swaps);
        if (trim != null) {
            options.add(trim);
        }
        Alternative drop = makeDropOption(context, swaps);
        if (drop != null) {
            options.add(drop);
        }
        options.add(best);
        return orderOptions(context, options);
    }

    /** 같은 종류 칸 자리에 새 장소를 넣음 (머묾은 그 칸과 같게), 나머지 새 장소는 빈자리에 */
    private Alternative makeSwapOption(Context context, Map<Stop, Stop> swaps) {
        List<Stop> stops = new ArrayList<>();
        for (Stop stop : context.remaining()) {
            Stop swapped = stop;
            for (Map.Entry<Stop, Stop> swap : swaps.entrySet()) {
                if (swap.getValue() == stop) {
                    swapped = copyStop(swap.getKey());
                    swapped.setStay(liveStopFinder.getStay(stop));
                }
            }
            stops.add(swapped);
        }
        Alternative option = calculatePlan(context, placeAll(context, stops));
        Map.Entry<Stop, Stop> first = swaps.entrySet().iterator().next();
        option.setShortName("바꾸기");
        option.setTitle(swaps.size() == 1 ? first.getValue().getName() + " → " + first.getKey().getName() : swaps.size() + "곳 바꾸기");
        option.setReason("겹치는 곳을 바꿔요.");
        option.setChanges(swaps.size());
        return option;
    }

    /** 둘 다 가되 시간 줄이기: 겹치는 곳을 먼저 줄이고, 그래도 늦으면 고정 일정 앞 칸을 차례로 줄임 */
    private Alternative makeTrimOption(Context context, Map<Stop, Stop> swaps) {
        List<Stop> stops = new ArrayList<>();
        for (Stop stop : context.remaining()) {
            Stop copy = copyStop(stop);
            if (swaps.containsValue(stop)) {
                copy.setStay(Math.min(liveStopFinder.getStay(stop), getBothStay(stop.getCategory())));
            }
            stops.add(copy);
        }
        List<Stop> placed = placeAll(context, stops);
        for (int i = 0; i < placed.size(); i++) {
            Stop stop = placed.get(i);
            if (stop.getId() == null) {
                Stop copy = copyStop(stop);
                if (swaps.containsKey(stop)) {
                    copy.setStay(Math.min(liveStopFinder.getStay(stop), getBothStay(stop.getCategory())));
                }
                placed.set(i, copy);
            }
        }
        int need = getOverMinutes(context, calculatePlan(context, placed));
        if (need > 0) {
            trimStays(placed, need + TRIM_BUFFER_MIN);
        }
        Alternative option = calculatePlan(context, placed);
        int changes = markStayChanges(context, option, placed);
        if (changes == 0) {
            return null; // 줄일 수 있는 곳이 없음
        }
        option.setChanges(changes);
        option.setShortName("시간 줄이기");
        option.setTitle(swaps.isEmpty() ? "시간 줄이기" : "둘 다 가고 시간 줄이기");
        option.setReason("머무는 시간만 줄여요.");
        return option;
    }

    /** 줄여야 하는 분: 고정 일정에 늦는 분, 고정 일정이 없으면 밤 10시를 넘는 분 */
    private int getOverMinutes(Context context, Alternative plan) {
        if (context.hasFixed()) {
            return plan.getSlackMin() < 0 ? -plan.getSlackMin() : 0;
        }
        return Math.max(0, getLastArrive(plan) - LAST_ARRIVE_MIN);
    }

    /** 고정 일정 앞 칸부터 최소 머묾까지 줄여서 need분을 채움 (꼭 갈 곳은 그대로). 고정 일정이 없으면 끝까지 */
    private void trimStays(List<Stop> stops, int need) {
        int left = need;
        for (Stop stop : stops) {
            if (Boolean.TRUE.equals(stop.getFixed()) || left <= 0) {
                break;
            }
            if (!IMPORTANCE_MUST.equals(stop.getImportance())) {
                int stay = liveStopFinder.getStay(stop);
                int cut = Math.min(Math.max(0, stay - getMinStay(stop.getCategory())), left);
                stop.setStay(stay - cut);
                left -= cut;
            }
        }
    }

    /** 머묾이 바뀐 칸에 "60분 → 40분"을 적음. 바뀐 칸 수(새 장소 제외)를 돌려줌 */
    private int markStayChanges(Context context, Alternative option, List<Stop> stops) {
        int changes = 0;
        for (int i = 0; i < stops.size(); i++) {
            Stop stop = stops.get(i);
            for (Stop original : context.remaining()) {
                if (original.getId().equals(stop.getId()) && liveStopFinder.getStay(original) != liveStopFinder.getStay(stop)) {
                    option.getStops().get(i).setChange(Geo.toDuration(liveStopFinder.getStay(original)) + " → " + Geo.toDuration(liveStopFinder.getStay(stop)));
                    changes++;
                }
            }
        }
        return changes;
    }

    /** 한 곳 빼기: 빼도 되는 곳 → 오래 머무는 곳 순. 겹치는 칸은 "바꾸기"가 있어서 고르지 않음 */
    private Alternative makeDropOption(Context context, Map<Stop, Stop> swaps) {
        Stop dropStop = null;
        for (Stop stop : context.remaining()) {
            if (isChangeable(stop) && !swaps.containsValue(stop) && (dropStop == null || isBetterDrop(stop, dropStop))) {
                dropStop = stop;
            }
        }
        if (dropStop == null) {
            return null;
        }
        List<Stop> kept = new ArrayList<>(context.remaining());
        kept.remove(dropStop);
        Alternative option = calculatePlan(context, placeAll(context, kept)); // 뺀 만큼 시간이 생겨서 자리를 다시 찾음
        option.setShortName("한 곳 빼기");
        option.setTitle(dropStop.getName() + " 빼기");
        option.setReason("한 곳을 빼요.");
        option.setChanges(1);
        return option;
    }

    private boolean isBetterDrop(Stop stop, Stop dropStop) {
        boolean isOptional = IMPORTANCE_OPTIONAL.equals(stop.getImportance());
        boolean isDropOptional = IMPORTANCE_OPTIONAL.equals(dropStop.getImportance());
        if (isOptional != isDropOptional) {
            return isOptional;
        }
        return liveStopFinder.getStay(stop) > liveStopFinder.getStay(dropStop);
    }

    /** 괜찮은 방법 중 맨 앞(바꾸기 → 줄이기 → 빼기 → 그대로)을 추천. 없으면 가장 덜 늦는 것 */
    private List<Alternative> orderOptions(Context context, List<Alternative> options) {
        Alternative recommended = null;
        for (Alternative option : options) {
            boolean isOnTime = context.hasFixed() ? option.getSlackMin() >= 0 && getLastArrive(option) < MINUTES_OF_DAY : getLastArrive(option) <= LAST_ARRIVE_MIN;
            if (recommended == null && isOnTime && option.getClosedCount() <= context.baseClosed()) {
                recommended = option;
            }
        }
        if (recommended == null) {
            for (Alternative option : options) {
                if (recommended == null || getLateness(context, option) < getLateness(context, recommended)) {
                    recommended = option;
                }
            }
        }
        recommended.setRecommended(true);
        List<Alternative> ordered = new ArrayList<>();
        ordered.add(recommended);
        for (Alternative option : options) {
            if (option != recommended) {
                ordered.add(option);
            }
        }
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setKey(KEYS[i]);
        }
        return ordered;
    }

    /** 작을수록 덜 늦음: 고정 일정이 있으면 늦는 분, 없으면 마지막 도착 시각 */
    private int getLateness(Context context, Alternative option) {
        return context.hasFixed() ? -option.getSlackMin() : getLastArrive(option);
    }

    // ---------------------------------------------------------------- 도우미

    /** 요청 장소 → 새 칸 목록. 이미 일정에 있는 곳·겹치는 이름은 빼고, 다 빠지면 오류 */
    private List<Stop> toNewStops(LinkFitRequest request, List<Stop> remaining, Trip trip) {
        if (request.getPlaces() == null || request.getPlaces().isEmpty()) {
            throw fail(400, "넣을 장소를 골라 주세요.");
        }
        if (request.getPlaces().size() > MAX_PLACES) {
            throw fail(400, "한 번에 " + MAX_PLACES + "곳까지 넣을 수 있어요.");
        }
        List<Stop> newStops = new ArrayList<>();
        for (LinkFitRequest.LinkPlace place : request.getPlaces()) {
            if (place.getName() == null || place.getName().isBlank() || place.getLat() == null || place.getLng() == null) {
                throw fail(400, "넣을 장소의 위치를 알 수 없어요.");
            }
            if (!containsName(remaining, place.getName()) && !containsName(newStops, place.getName())) {
                newStops.add(toNewStop(place, trip));
            }
        }
        if (newStops.isEmpty()) {
            throw fail(400, "이미 남은 일정에 있어요.");
        }
        return newStops;
    }

    private boolean containsName(List<Stop> stops, String name) {
        for (Stop stop : stops) {
            if (name.equals(stop.getName())) {
                return true;
            }
        }
        return false;
    }

    /** 오늘 칸 중 남은 칸만 순서대로 (지금 있는 칸·다녀온 칸·뺀 칸 제외) */
    private List<Stop> getRemainingStops(List<Stop> todayStops, Stop current) {
        List<Stop> remaining = new ArrayList<>();
        for (Stop stop : todayStops) {
            if (liveStopFinder.isRemaining(stop, current)) {
                remaining.add(stop);
            }
        }
        return remaining;
    }

    private Stop toNewStop(LinkFitRequest.LinkPlace place, Trip trip) {
        Stop stop = new Stop();
        stop.setDay(liveStopFinder.getToday(trip));
        stop.setPlaceId(place.getPlaceId());
        stop.setName(place.getName());
        stop.setCategory(place.getCategory());
        stop.setAddress(place.getAddress());
        stop.setLat(place.getLat());
        stop.setLng(place.getLng());
        stop.setStay(KIND_CAFE.equals(toKind(place.getCategory())) ? CAFE_STAY_MIN : DEFAULT_STAY_MIN);
        stop.setFixed(false);
        stop.setState("todo");
        return stop;
    }

    private Stop copyStop(Stop stop) {
        return alternativeCalculator.copyStops(List.of(stop)).get(0);
    }

    /**
     * 계산 + 영업 정보 + 새 칸 표시. 새 칸은 계획 시각이 없어서 먼저 지금 시각으로 계산한 뒤
     * 나온 도착 시각을 계획 시각으로 두고 한 번 더 계산 (마지막 칸이면 여유 계산에 계획 시각을 씀).
     * 새 칸은 목록끼리 같이 써서, 복사본에 시각을 적어 원본이 안 바뀌게 함
     */
    private Alternative calculatePlan(Context context, List<Stop> stops) {
        List<Stop> timed = new ArrayList<>();
        for (Stop stop : stops) {
            Stop item = stop;
            if (stop.getId() == null) {
                item = copyStop(stop);
                item.setTime(Geo.toHhmm(Geo.nowMinutes()));
            }
            timed.add(item);
        }
        Alternative plan = alternativeCalculator.calculate(context.trip(), context.current(), timed, context.lat(), context.lng());
        for (int i = 0; i < timed.size(); i++) {
            if (timed.get(i).getId() == null) {
                timed.get(i).setTime(plan.getStops().get(i).getEta());
            }
        }
        plan = alternativeCalculator.calculate(context.trip(), context.current(), timed, context.lat(), context.lng());
        for (AltStop stop : plan.getStops()) {
            if (stop.getId() == null) {
                stop.setChange("새로 추가");
            }
        }
        fillOpenInfo(plan);
        if (getLastArrive(plan) >= MINUTES_OF_DAY) {
            plan.setFinalArrive("다음날 " + Geo.toHhmm(getLastArrive(plan) - MINUTES_OF_DAY));
        } else if (!context.hasFixed()) {
            plan.setFinalArrive(Geo.toHhmm(getLastArrive(plan)));
        }
        return plan;
    }

    /** 칸마다 도착 시각 기준 영업 정보를 채우고, 문 닫은 곳 수를 대안에도 적고 돌려줌 */
    private int fillOpenInfo(Alternative plan) {
        int closedCount = 0;
        for (AltStop stop : plan.getStops()) {
            OpenInfo open = findOpenInfo(stop);
            if (open == null) {
                continue;
            }
            stop.setCloseAt(open.getCloseAt());
            stop.setClosedReason(open.getClosedReason());
            stop.setClosingSoon(Boolean.TRUE.equals(open.getClosingSoon()));
            if (Boolean.FALSE.equals(open.getOpen())) {
                closedCount++;
            }
        }
        plan.setClosedCount(closedCount);
        return closedCount;
    }

    /** DB에 없는 장소·지워진 장소는 영업시간을 몰라서 null */
    private OpenInfo findOpenInfo(AltStop stop) {
        if (stop.getPlaceId() == null) {
            return null;
        }
        try {
            Place place = placeService.getPlace(stop.getPlaceId());
            int stay = stop.getStay() == null ? 0 : stop.getStay();
            return placeService.getOpenInfo(place, Geo.now().toLocalDate(), Geo.toMinutes(stop.getEta()), stay);
        } catch (ApiException e) {
            return null;
        }
    }

    private boolean isChangeable(Stop stop) {
        return !Boolean.TRUE.equals(stop.getFixed()) && !IMPORTANCE_MUST.equals(stop.getImportance());
    }

    private String toKind(String category) {
        String text = category == null ? "" : category;
        if (text.matches(CAFE_WORDS)) {
            return KIND_CAFE;
        }
        return text.matches(FOOD_WORDS) ? KIND_FOOD : KIND_OTHER;
    }

    private int getMinStay(String category) {
        String kind = toKind(category);
        if (KIND_FOOD.equals(kind)) {
            return MEAL_MIN_STAY;
        }
        return KIND_CAFE.equals(kind) ? CAFE_MIN_STAY : OTHER_MIN_STAY;
    }

    private int getBothStay(String category) {
        return KIND_CAFE.equals(toKind(category)) ? BOTH_CAFE_STAY : BOTH_MEAL_STAY;
    }

    /** 화면 맨 위 한 줄 (짧게) */
    private String getMessage(boolean fits, Map<Stop, Stop> swaps, Alternative best, Stop fixedStop) {
        if (!fits) {
            if (fixedStop != null && best.getSlackMin() < 0) {
                return fixedStop.getName() + " " + Geo.toDuration(-best.getSlackMin()) + " 늦어요";
            }
            return getLastArrive(best) > LAST_ARRIVE_MIN ? "밤늦게 끝나요" : "문 닫는 곳이 생겨요";
        }
        if (!swaps.isEmpty()) {
            return swaps.values().iterator().next().getName() + "과(와) 겹쳐요";
        }
        return "다른 일정은 그대로예요";
    }
}
