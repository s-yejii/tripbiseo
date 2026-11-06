package com.tripbiseo.plan.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.common.external.RouteClient;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.plan.dto.RouteOption;
import com.tripbiseo.plan.dto.RouteOptionsResponse;
import com.tripbiseo.trip.dto.OpenInfo;
import com.tripbiseo.trip.dto.Place;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.service.PlaceService;

import lombok.RequiredArgsConstructor;

/**
 * ③ 하루 동선 정리 (11/04): 시각 칸은 그대로 두고, 칸에 들어갈 장소 순서만 실제 좌표로 다시 정해요.
 *   1) 최근접 이웃: 출발지(없으면 장소마다 시험)에서 가장 가까운 곳을 차례로
 *   2) 2-opt: 구간을 뒤집거나 두 곳을 맞바꿔서 더 짧아지면 바꿈 (선이 꼬인 곳이 풀려요)
 *   지키는 것: 시간 고정·숙소는 그 자리 그대로, 식사는 점심·저녁 때 안에서, 술집은 저녁부터, 카페 연달아 안 감, 그 시각에 영업하는 곳만.
 *   거리는 직선 거리로 비교해요 (순서를 정하는 데는 충분하고 외부 API를 안 불러요).
 * AI 일정 만들기와 직접 고르기의 [동선 정리]가 같이 써요.
 */
@Component
@RequiredArgsConstructor
public class RouteOrderer {

    private static final Logger log = LoggerFactory.getLogger(RouteOrderer.class);

    private final PlaceService placeService;
    private final RouteClient routeClient; // 이동 시간 어림값

    private static final int LUNCH_START_MIN = 11 * 60 + 30;
    private static final int LUNCH_END_MIN = 13 * 60 + 30;
    private static final int DINNER_START_MIN = 17 * 60 + 30;
    private static final int DINNER_END_MIN = 19 * 60 + 30;
    private static final int BAR_FROM_MIN = 17 * 60 + 30;
    private static final int MAX_OPTIONS = 5;       // 동선 후보 수
    private static final int CHECK_OPTIONS = 20;    // 거리로 먼저 추린 뒤 시간을 잴 후보 수
    private static final int MAX_LIST_STOPS = 8;    // 옮길 칸이 이보다 많으면 순서를 다 세어 보지 않음
    private static final List<String> DEFAULT_TRANSPORTS = List.of("subway", "bus", "walk");
    private static final int MIN_STOPS = 3;       // 세 곳부터 순서를 바꿀 수 있어요
    private static final int MAX_ROUNDS = 30;     // 2-opt를 끝없이 돌지 않게
    private static final double MIN_SAVE_M = 1;   // 이만큼은 짧아져야 바꿈
    private static final String STAY_CATEGORY = "숙소";
    private static final String TIME_PATTERN = "\\d{2}:\\d{2}";

    /** 하루 일정의 새 순서: 자리 i에 들어갈 장소의 원래 번호 목록. start는 출발점 {lat, lng} (없으면 null) */
    public List<Integer> getOrder(List<Stop> stops, double[] start, LocalDate date) {
        List<Integer> identity = new ArrayList<>();
        for (int i = 0; i < stops.size(); i++) {
            identity.add(i);
        }
        if (stops.size() < MIN_STOPS || hasNoPoint(stops)) {
            return identity;
        }
        DayRule rule = new DayRule(stops, date);
        List<Integer> best = identity;
        for (int first = 0; first < stops.size(); first++) {
            List<Integer> nearest = makeNearestOrder(stops, start, rule, start == null ? first : -1);
            if (nearest != null && rule.isAllowed(nearest) && lengthOf(stops, nearest, start) < lengthOf(stops, best, start)) {
                best = nearest;
            }
            if (start != null) {
                break; // 출발점이 있으면 한 번만
            }
        }
        return improve(stops, best, start, rule);
    }

    /** 이동 시간이 짧은 순서 후보 (최대 5개, 짧은 순). 지금 순서도 들어갈 수 있어요. 규칙을 지키는 순서만 */
    public RouteOptionsResponse getOptions(List<Stop> stops, double[] start, LocalDate date, List<String> transports) {
        RouteOptionsResponse response = new RouteOptionsResponse();
        List<Integer> identity = new ArrayList<>();
        for (int i = 0; i < stops.size(); i++) {
            identity.add(i);
        }
        response.setOptions(new ArrayList<>());
        if (hasNoPoint(stops)) {
            return response;
        }
        response.setCurrentMinutes(minutesOf(stops, identity, start, transports));
        List<RouteOption> options = new ArrayList<>();
        for (List<Integer> order : listAllowedOrders(stops, start, date)) {
            RouteOption option = new RouteOption();
            option.setOrder(order);
            option.setMeters(Math.round(lengthOf(stops, order, start)));
            options.add(option);
        }
        options.sort((first, second) -> Long.compare(first.getMeters(), second.getMeters())); // 짧은 거리 순
        List<RouteOption> shortest = new ArrayList<>(options.subList(0, Math.min(options.size(), CHECK_OPTIONS)));
        for (RouteOption option : shortest) {
            option.setMinutes(minutesOf(stops, option.getOrder(), start, transports));
        }
        shortest.sort((first, second) -> first.getMinutes() - second.getMinutes()); // 짧은 시간 순
        response.setOptions(new ArrayList<>(shortest.subList(0, Math.min(shortest.size(), MAX_OPTIONS))));
        return response;
    }

    /** 하루 일정을 새 순서로 바꿈: 시각·머무는 분은 자리에 그대로, 장소만 바뀜 */
    public List<Stop> sortDay(List<Stop> stops, double[] start, LocalDate date) {
        List<Integer> order = getOrder(stops, start, date);
        logChange(stops, order, start);
        List<Stop> sorted = new ArrayList<>();
        List<String> times = new ArrayList<>();
        List<Integer> stays = new ArrayList<>();
        for (Stop stop : stops) {
            times.add(stop.getTime());
            stays.add(stop.getStay());
        }
        for (int i = 0; i < order.size(); i++) {
            Stop stop = stops.get(order.get(i));
            stop.setTime(times.get(i));
            stop.setStay(stays.get(i));
            sorted.add(stop);
        }
        return sorted;
    }

    /** 순서의 전체 길이(m): 출발점 → 첫 곳 → … → 마지막 곳 */
    public double lengthOf(List<Stop> stops, List<Integer> order, double[] start) {
        double meters = 0;
        double[] point = start;
        for (int index : order) {
            Stop stop = stops.get(index);
            if (point != null) {
                meters += Geo.distanceMeters(point[0], point[1], stop.getLat(), stop.getLng());
            }
            point = new double[] {stop.getLat(), stop.getLng()};
        }
        return meters;
    }

    // ---------------------------------------------------------------- 도우미

    /** 이동 시간 어림값: 구간마다 수단을 고르고 거리로 계산 (외부 길찾기 안 부름) */
    private int minutesOf(List<Stop> stops, List<Integer> order, double[] start, List<String> transports) {
        List<String> modes = transports == null || transports.isEmpty() ? DEFAULT_TRANSPORTS : transports;
        int minutes = 0;
        double[] point = start;
        for (int index : order) {
            Stop stop = stops.get(index);
            if (point != null) {
                double meters = Geo.distanceMeters(point[0], point[1], stop.getLat(), stop.getLng());
                minutes += routeClient.estimate(point[0], point[1], stop.getLat(), stop.getLng(), routeClient.chooseMode(null, meters, modes)).getMinutes();
            }
            point = new double[] {stop.getLat(), stop.getLng()};
        }
        return minutes;
    }

    /** 규칙을 지키는 순서 전부 (옮길 칸이 많으면 지금 순서·가장 짧은 순서만) */
    private List<List<Integer>> listAllowedOrders(List<Stop> stops, double[] start, LocalDate date) {
        DayRule rule = new DayRule(stops, date);
        List<List<Integer>> orders = new ArrayList<>();
        int movable = 0;
        for (int i = 0; i < stops.size(); i++) {
            movable += rule.isAnchor(i) ? 0 : 1;
        }
        if (movable > MAX_LIST_STOPS) {
            orders.add(getOrder(stops, start, date));
            return orders;
        }
        addOrders(rule, stops.size(), new ArrayList<>(), orders);
        return orders;
    }

    /** 자리를 앞에서부터 하나씩 채우며 끝까지 간 순서를 모아요 (되부름: 다음 자리를 채우러 자기 자신을 다시 불러요) */
    private void addOrders(DayRule rule, int size, List<Integer> order, List<List<Integer>> orders) {
        int place = order.size();
        if (place == size) {
            if (rule.isAllowed(order)) {
                orders.add(new ArrayList<>(order));
            }
            return;
        }
        for (int index = 0; index < size; index++) {
            boolean isOk = rule.isAnchor(place) ? index == place : !rule.isAnchor(index) && !order.contains(index) && (index == place || rule.isOkAt(index, place));
            if (isOk) {
                order.add(index);
                addOrders(rule, size, order, orders);
                order.remove(order.size() - 1);
            }
        }
    }

    /** 얼마나 짧아졌는지 서버 기록에 (번호와 거리만, 좌표·이름은 안 남김) */
    private void logChange(List<Stop> stops, List<Integer> order, double[] start) {
        if (hasNoPoint(stops)) {
            return;
        }
        List<Integer> before = new ArrayList<>();
        for (int i = 0; i < stops.size(); i++) {
            before.add(i);
        }
        log.info("동선 정리 {} : {}m → {}m", order, Math.round(lengthOf(stops, before, start)), Math.round(lengthOf(stops, order, start)));
    }

    private boolean hasNoPoint(List<Stop> stops) {
        for (Stop stop : stops) {
            if (stop.getLat() == null || stop.getLng() == null) {
                return true;
            }
        }
        return false;
    }

    /** 최근접 이웃. firstIndex가 0 이상이면 그 장소부터 시작. 넣을 곳이 없는 자리가 생기면 null */
    private List<Integer> makeNearestOrder(List<Stop> stops, double[] start, DayRule rule, int firstIndex) {
        List<Integer> order = new ArrayList<>();
        List<Integer> left = new ArrayList<>();
        for (int i = 0; i < stops.size(); i++) {
            if (!rule.isAnchor(i)) {
                left.add(i);
            }
        }
        double[] point = start;
        for (int place = 0; place < stops.size(); place++) {
            Integer picked = rule.isAnchor(place) ? Integer.valueOf(place) : findNearest(stops, left, place, point, rule, order.isEmpty() ? firstIndex : -1);
            if (picked == null) {
                return null;
            }
            order.add(picked);
            left.remove(picked);
            point = new double[] {stops.get(picked).getLat(), stops.get(picked).getLng()};
        }
        return order;
    }

    /** 이 자리에 넣을 수 있는 곳 중 point에서 가장 가까운 곳 (첫 자리를 정해 줬으면 그곳) */
    private Integer findNearest(List<Stop> stops, List<Integer> left, int place, double[] point, DayRule rule, int firstIndex) {
        if (firstIndex >= 0 && left.contains(firstIndex)) {
            return rule.isOkAt(firstIndex, place) ? Integer.valueOf(firstIndex) : null;
        }
        Integer nearest = null;
        double nearestMeters = Double.MAX_VALUE;
        for (Integer index : left) {
            double meters = point == null ? 0 : Geo.distanceMeters(point[0], point[1], stops.get(index).getLat(), stops.get(index).getLng());
            if (rule.isOkAt(index, place) && meters < nearestMeters) {
                nearest = index;
                nearestMeters = meters;
            }
        }
        return nearest;
    }

    /** 2-opt: 구간 뒤집기·두 곳 맞바꾸기로 짧아지면 바꾸기를 더 안 줄어들 때까지 */
    private List<Integer> improve(List<Stop> stops, List<Integer> order, double[] start, DayRule rule) {
        List<Integer> best = order;
        double bestLength = lengthOf(stops, best, start);
        for (int round = 0; round < MAX_ROUNDS; round++) {
            boolean isChanged = false;
            for (int i = 0; i < best.size() - 1; i++) {
                for (int j = i + 1; j < best.size(); j++) {
                    List<Integer> reversed = reverse(best, i, j);
                    List<Integer> swapped = swap(best, i, j);
                    for (List<Integer> candidate : List.of(reversed, swapped)) {
                        double length = lengthOf(stops, candidate, start);
                        if (length < bestLength - MIN_SAVE_M && rule.isAllowed(candidate)) {
                            best = candidate;
                            bestLength = length;
                            isChanged = true;
                        }
                    }
                }
            }
            if (!isChanged) {
                break;
            }
        }
        return best;
    }

    private List<Integer> reverse(List<Integer> order, int from, int to) {
        List<Integer> result = new ArrayList<>(order);
        for (int i = from; i <= to; i++) {
            result.set(i, order.get(to - (i - from)));
        }
        return result;
    }

    private List<Integer> swap(List<Integer> order, int first, int second) {
        List<Integer> result = new ArrayList<>(order);
        result.set(first, order.get(second));
        result.set(second, order.get(first));
        return result;
    }

    private boolean isLunch(int minutes) {
        return minutes >= LUNCH_START_MIN && minutes < LUNCH_END_MIN;
    }

    private boolean isDinner(int minutes) {
        return minutes >= DINNER_START_MIN && minutes < DINNER_END_MIN;
    }

    /** 하루치 규칙: 어떤 장소가 어떤 자리에 들어갈 수 있는지. 영업 확인은 같은 조합을 다시 묻지 않게 기억해요 */
    private class DayRule {
        private final List<Stop> stops;
        private final LocalDate date;
        private final Map<Integer, Place> places = new HashMap<>();
        private final Map<String, Boolean> openMemo = new HashMap<>();
        private final int[] minutes; // 자리마다 시작 시각(분), 모르면 -1
        private int lunchMeals = 0;  // 원래 점심 때 식사 수: 순서를 바꿔도 끼니가 안 빠지게
        private int dinnerMeals = 0;

        DayRule(List<Stop> stops, LocalDate date) {
            this.stops = stops;
            this.date = date;
            this.minutes = new int[stops.size()];
            for (int i = 0; i < stops.size(); i++) {
                String time = stops.get(i).getTime();
                minutes[i] = time != null && time.matches(TIME_PATTERN) ? Geo.toMinutes(time) : -1;
                places.put(i, findPlace(stops.get(i)));
                if (isMeal(i) && isLunch(minutes[i])) {
                    lunchMeals++;
                }
                if (isMeal(i) && isDinner(minutes[i])) {
                    dinnerMeals++;
                }
            }
        }

        private Place findPlace(Stop stop) {
            if (stop.getPlaceId() == null) {
                return null;
            }
            try {
                return placeService.getPlace(stop.getPlaceId());
            } catch (ApiException e) {
                return null; // 없어진 장소는 종류·영업시간을 모르는 곳으로
            }
        }

        boolean isAnchor(int index) {
            Stop stop = stops.get(index);
            return Boolean.TRUE.equals(stop.getFixed()) || STAY_CATEGORY.equals(stop.getCategory());
        }

        private boolean isKind(int index, String kind) {
            Place place = places.get(index);
            return place != null && kind.equals(place.getKind());
        }

        private boolean isMeal(int index) {
            return isKind(index, "food");
        }

        /** 장소 index가 자리 place에 들어갈 수 있는지 */
        boolean isOkAt(int index, int place) {
            int time = minutes[place];
            if (time < 0) {
                return true;
            }
            boolean wasMealTime = isLunch(minutes[index]) || isDinner(minutes[index]);
            if (isMeal(index) && wasMealTime && !isLunch(time) && !isDinner(time)) {
                return false; // 식사는 식사 때 안에서만 (원래 다른 때에 넣은 식사는 그대로 둠)
            }
            if (isKind(index, "bar") && time < BAR_FROM_MIN) {
                return false;
            }
            return isOpen(index, place, time);
        }

        private boolean isOpen(int index, int place, int time) {
            Place found = places.get(index);
            Integer stay = stops.get(place).getStay();
            if (found == null || date == null || stay == null) {
                return true;
            }
            String key = index + "/" + time + "/" + stay;
            Boolean memo = openMemo.get(key);
            if (memo == null) {
                OpenInfo open = placeService.getOpenInfo(found, date, time, stay);
                memo = !Boolean.FALSE.equals(open.getOpen()) && (open.getMinutesLeft() == null || open.getMinutesLeft() >= stay);
                openMemo.put(key, memo);
            }
            return memo;
        }

        /** 순서 전체 검사: 고정 칸은 제자리, 자리마다 규칙, 점심·저녁 수 유지 */
        boolean isAllowed(List<Integer> order) {
            int lunch = 0;
            int dinner = 0;
            for (int place = 0; place < order.size(); place++) {
                int index = order.get(place);
                boolean isAnchorMoved = isAnchor(index) && index != place;
                if (isAnchorMoved || isAnchor(place) && index != place) {
                    return false;
                }
                if (index != place && !isOkAt(index, place)) {
                    return false;
                }
                boolean isCafeAgain = place > 0 && isKind(index, "cafe") && isKind(order.get(place - 1), "cafe");
                if (isCafeAgain) {
                    return false; // 카페 다음에 또 카페는 안 가요
                }
                lunch += isMeal(index) && minutes[place] >= 0 && isLunch(minutes[place]) ? 1 : 0;
                dinner += isMeal(index) && minutes[place] >= 0 && isDinner(minutes[place]) ? 1 : 0;
            }
            return lunch == lunchMeals && dinner == dinnerMeals;
        }
    }
}
