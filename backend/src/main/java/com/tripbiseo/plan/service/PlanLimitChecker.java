package com.tripbiseo.plan.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.common.external.FoundLocation;
import com.tripbiseo.common.external.Route;
import com.tripbiseo.common.external.RouteClient;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.plan.dto.FixedVisit;
import com.tripbiseo.plan.dto.PlanConditions;
import com.tripbiseo.plan.dto.PlanFinish;
import com.tripbiseo.plan.dto.PlanGenerateResponse;
import com.tripbiseo.plan.dto.PlanNote;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.service.PlaceService;

import lombok.RequiredArgsConstructor;

/**
 * ③ 일정 만들기의 제한 두 가지 (11/04). 둘 다 서버가 경로로 계산해요 (Gemini는 판단하지 않음).
 * ① 끝 장소·도착 마감 ("KTX 19시 광명역" → 광명역 18:30): 끝 장소를 카카오로 찾고, 이동 시간을 빼서
 *    마지막 날 일정이 끝나야 하는 시각을 정함 → 다 짠 뒤 마지막 일정에서 실제로 늦지 않는지 다시 보고, 늦으면 뒤에서부터 뺌
 * ② 한 번에 걷는 시간 상한 ("오래 걷기 싫어" → 15분): 칸 사이 경로의 걷는 거리로 분을 계산해 넘는 구간을 알려 줌
 */
@Component
@RequiredArgsConstructor
public class PlanLimitChecker {

    private static final int FINISH_BUFFER_MIN = 10;        // 끝 장소에 도착 시각보다 10분 일찍 (표 찾기·짐)
    private static final int FINISH_STAY_MIN = 10;          // 끝 칸 머무는 분 (일정표 표시용, 칸 규칙 최소 10분)
    private static final int UNKNOWN_TRAVEL_MIN = 60;       // 끝 장소 위치를 못 찾았을 때 이동 시간 추정
    private static final int WALK_M_PER_MIN = 75;           // 걷는 속도 4.5km/h (RouteClient와 같음)
    private static final int MAX_TRIMS = 3;                 // 마감에 맞추려고 뒤에서 뺄 수 있는 칸 수
    private static final int MAX_WALK_NOTES = 3;
    private static final String MUST = "must";
    private static final String STAY_CATEGORY = "숙소";
    private static final String FINISH_CATEGORY = "여행 끝";

    private final PlaceService placeService;
    private final RouteClient routeClient;

    // ---------------------------------------------------------------- ① 끝 장소

    /** 끝 장소 찾기 + 마지막 날 일정이 끝나야 하는 시각. from: 그날 출발점(지역 중심 등, 없으면 null). 끝 장소 조건이 없으면 null */
    public PlanFinish findFinish(PlanConditions conditions, double[] from, List<String> transports) {
        FixedVisit finish = conditions.getFinish();
        if (finish == null || finish.getName() == null || finish.getName().isBlank() || !isTime(finish.getTime())) {
            return null;
        }
        PlanFinish result = new PlanFinish();
        result.setName(finish.getName().trim());
        result.setTime(finish.getTime());
        FoundLocation found = searchLocation(result.getName(), from);
        if (found != null) {
            result.setName(found.getName());
            result.setAddress(found.getAddress());
            result.setLat(found.getLat());
            result.setLng(found.getLng());
            result.setFound(true);
        }
        int travel = UNKNOWN_TRAVEL_MIN;
        if (found != null && from != null) {
            travel = routeClient.getRoute(from[0], from[1], found.getLat(), found.getLng(), null, transports, true).getMinutes();
        }
        result.setTravelMin(travel);
        result.setLastEndMin(Geo.toMinutes(finish.getTime()) - travel - FINISH_BUFFER_MIN);
        return result;
    }

    /** 대화에서 되묻는 말: "광명역에 18:30까지 가려면 마지막 일정은 17:40쯤 끝나야 해요 (이동 약 40분)" */
    public String makeFinishText(PlanFinish finish) {
        String travel = finish.isFound() ? "이동 약 " + finish.getTravelMin() + "분" : "위치를 못 찾아 이동 " + UNKNOWN_TRAVEL_MIN + "분으로 잡음";
        return finish.getName() + "에 " + finish.getTime() + "까지 가려면 마지막 일정은 " + Geo.toHhmm(finish.getLastEndMin())
             + "쯤 끝나야 해요 (" + travel + ")";
    }

    /**
     * 다 짠 일정을 끝 장소에 맞춤: 마지막 날 마지막 일정 끝 + 이동이 도착 시각을 넘으면 그 칸을 뺌 (꼭 갈 곳·고정은 안 뺌).
     * 맞춘 뒤 끝 칸을 더하고, 화면에 보일 한 줄 결과를 돌려줌
     */
    public String fitFinish(PlanGenerateResponse response, PlanFinish finish, int days, List<String> transports, List<PlanNote> notes) {
        int deadline = Geo.toMinutes(finish.getTime());
        List<String> removed = new ArrayList<>();
        int arrive = -1;
        boolean isMustLate = false; // 꼭 갈 곳 때문에 늦어서 이미 안내함
        for (int i = 0; i <= MAX_TRIMS; i++) {
            Stop last = findLastStop(response.getStops(), days);
            if (last == null || !finish.isFound()) {
                break;
            }
            arrive = arriveAt(last, finish, transports);
            if (arrive + FINISH_BUFFER_MIN <= deadline || i == MAX_TRIMS) { // 여유 10분을 남기고 도착하면 됨
                break;
            }
            if (MUST.equals(last.getImportance()) || Boolean.TRUE.equals(last.getFixed())) {
                notes.add(makeNote("꼭 갈 곳 '" + last.getName() + "' 때문에 " + finish.getName() + "에 " + Geo.toHhmm(arrive) + "쯤 도착해요. 일정을 줄여 주세요"));
                isMustLate = true;
                break;
            }
            response.getStops().remove(last);
            removed.add(last.getName());
        }
        response.getStops().add(toFinishStop(finish, days));
        if (!removed.isEmpty()) {
            notes.add(makeNote(finish.getName() + " " + finish.getTime() + "에 맞추려고 뺐어요: " + String.join(", ", removed)));
        }
        if (arrive + FINISH_BUFFER_MIN > deadline && !isMustLate) {
            notes.add(makeNote("일정을 줄여도 " + finish.getName() + "에 " + Geo.toHhmm(arrive) + "쯤 도착해요. 마지막 일정을 직접 빼 주세요"));
        }
        if (arrive < 0) {
            return finish.isFound() ? "마지막 날 일정이 없어요" : "위치를 못 찾아 " + Geo.toHhmm(finish.getLastEndMin()) + "까지만 일정을 넣었어요";
        }
        return Geo.toHhmm(arrive) + " 도착 예정" + (removed.isEmpty() ? "" : " · " + removed.size() + "곳 뺌");
    }

    /** 마지막 날에서 시각이 가장 늦은 일정 (숙소·끝 칸 빼고). 없으면 null */
    private Stop findLastStop(List<Stop> stops, int days) {
        Stop last = null;
        for (Stop stop : stops) {
            boolean isPlace = !STAY_CATEGORY.equals(stop.getCategory()) && !FINISH_CATEGORY.equals(stop.getCategory());
            if (stop.getDay() == days && isPlace && stop.getLat() != null && stop.getTime() != null) {
                if (last == null || stop.getTime().compareTo(last.getTime()) > 0) {
                    last = stop;
                }
            }
        }
        return last;
    }

    /** 이 일정이 끝나고 끝 장소에 도착하는 시각(분) = 시작 + 머무는 분 + 이동 */
    private int arriveAt(Stop last, PlanFinish finish, List<String> transports) {
        Route route = routeClient.getRoute(last.getLat(), last.getLng(), finish.getLat(), finish.getLng(), null, transports, true);
        return Geo.toMinutes(last.getTime()) + last.getStay() + route.getMinutes();
    }

    private Stop toFinishStop(PlanFinish finish, int days) {
        Stop stop = new Stop();
        stop.setDay(days);
        stop.setName(finish.getName());
        stop.setCategory(FINISH_CATEGORY);
        stop.setAddress(finish.getAddress());
        stop.setLat(finish.getLat());
        stop.setLng(finish.getLng());
        stop.setTime(finish.getTime());
        stop.setStay(FINISH_STAY_MIN);
        stop.setFixed(true);
        stop.setImportance(MUST);
        stop.setState("todo");
        stop.setMemo("여행 끝 · " + finish.getTime() + "까지 도착");
        return stop;
    }

    /** 카카오로 끝 장소 찾기 (역·터미널·공항). 키가 없거나 못 찾으면 null */
    private FoundLocation searchLocation(String name, double[] from) {
        try {
            List<FoundLocation> found = placeService.getLocationList(name, from == null ? null : from[0], from == null ? null : from[1]);
            return found.isEmpty() ? null : found.get(0);
        } catch (ApiException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- ② 걷는 시간

    /** 같은 날 칸 사이 경로마다 걷는 분을 계산해 상한을 넘는 구간을 notes에 (3개까지). 화면에 보일 한 줄 결과를 돌려줌 */
    public String checkWalk(PlanGenerateResponse response, int maxWalkMin, List<String> transports, List<PlanNote> notes) {
        List<Stop> stops = new ArrayList<>(response.getStops());
        stops.sort((first, second) -> first.getDay().equals(second.getDay())
                ? first.getTime().compareTo(second.getTime()) : first.getDay() - second.getDay());
        int longest = 0;
        int over = 0;
        for (int i = 1; i < stops.size(); i++) {
            Stop from = stops.get(i - 1);
            Stop to = stops.get(i);
            if (!from.getDay().equals(to.getDay()) || from.getLat() == null || to.getLat() == null) {
                continue;
            }
            Route route = routeClient.getRoute(from.getLat(), from.getLng(), to.getLat(), to.getLng(), to.getMode(), transports, true);
            int walkMin = (int) Math.ceil((double) route.getWalkMeters() / WALK_M_PER_MIN);
            longest = Math.max(longest, walkMin);
            if (walkMin > maxWalkMin && ++over <= MAX_WALK_NOTES) { // ++over: 먼저 1 더하고 비교
                notes.add(makeNote("'" + from.getName() + "' → '" + to.getName() + "'은 약 " + walkMin + "분 걸어요. 택시를 타거나 장소를 바꿔 보세요"));
            }
        }
        return over == 0 ? "가장 오래 걷는 구간 " + longest + "분" : over + "구간이 " + maxWalkMin + "분을 넘어요 (가장 긴 구간 " + longest + "분)";
    }

    private boolean isTime(String time) {
        return time != null && time.matches("([01]\\d|2[0-3]):[0-5]\\d");
    }

    private PlanNote makeNote(String text) {
        PlanNote note = new PlanNote();
        note.setText(text);
        return note;
    }
}
