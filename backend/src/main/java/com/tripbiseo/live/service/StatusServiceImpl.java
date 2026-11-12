package com.tripbiseo.live.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.common.external.Route;
import com.tripbiseo.common.external.RouteClient;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.live.dto.AreaInfo;
import com.tripbiseo.live.dto.FixedStatus;
import com.tripbiseo.live.dto.LeaveInfo;
import com.tripbiseo.live.dto.LiveAlert;
import com.tripbiseo.live.dto.LocationRequest;
import com.tripbiseo.live.dto.NextRoute;
import com.tripbiseo.live.dto.StatusContext;
import com.tripbiseo.live.dto.StatusResponse;
import com.tripbiseo.live.dto.StatusStop;
import com.tripbiseo.member.dto.Notification;
import com.tripbiseo.member.service.NotificationService;
import com.tripbiseo.trip.dto.OpenInfo;
import com.tripbiseo.trip.dto.Place;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;
import com.tripbiseo.trip.service.PlaceService;

import lombok.RequiredArgsConstructor;

/**
 * ④ 지금 상황 다시 계산 (POST /live/status). LiveServiceImpl이 너무 커져서 나눔.
 * 예전 코드: git show backend-v0:backend/src/main/java/com/tripbiseo/service/LiveService.java (status/today/start/alert/rain)
 */
@Service
@RequiredArgsConstructor
public class StatusServiceImpl implements StatusService {

    private static final int PRECISE_ROUTE_COUNT = 3;   // 가까운 3구간만 진짜 길찾기, 나머지는 거리로 추정
    private static final Set<String> MEAL_KINDS = Set.of("food", "cafe", "bar"); // 놓침 카드를 띄울 장소 종류 (PlaceMapper kindCase 값, 11/12)
    private static final int LATE_STEP_MIN = 10;        // late- 알림과 같은 간격: 10분 더 늦어질 때마다만 가장 빠른 방법을 다시 계산

    private final AutoAdjuster autoAdjuster; // 최근 자동 일정 조정 안내 (③ 11/08)
    private final LiveTripReader liveTripReader;         // 공통 오류 검사 + 진행 중인 여행 (④ 도우미)
    private final PlaceService placeService;             // 장소 영업 정보 (②)
    private final RouteClient routeClient;               // 이동 시간 (후보 비교는 isPrecise=false)
    private final LiveStopFinder liveStopFinder;         // 오늘 칸·지금 칸·남은 칸 고르기 (④ 도우미)
    private final LiveAlertMaker liveAlertMaker;         // 출발 알림·맨 위 알림 만들기 (④ 도우미)
    private final LiveWeatherChecker liveWeatherChecker; // 비 소식 + 비 맞을 야외 일정 (④ 도우미)
    private final NotificationService notificationService; // 맨 위 알림을 알림함에도 남김 (①)
    private final TransferFinder transferFinder;         // 가장 빠른 방법 (④ 도우미, 놓침 판단)
    // "칸 id-늦음 단계" → 가장 빠른 방법으로 가도 남는 여유(분). 여러 요청이 동시에 써도 안전한 Map이라 ConcurrentHashMap (TMAP 호출 줄이기)
    private final Map<String, Integer> fastestSlackCache = new ConcurrentHashMap<>();

    /** 지금 상황 다시 계산 */
    @Override
    public StatusResponse getStatus(long memberId, long tripId, LocationRequest request) {
        // 1단계: now, currentStopId, stops[] / 2단계: eta, next / 3단계: fixed, reach, hint, area
        // 4단계: leave, alert / 5단계: 영업 정보 / 6단계: weather / 7단계: replan
        Trip trip = liveTripReader.getOngoingTrip(memberId, tripId, request.getLat(), request.getLng()); // 공통 오류 검사
        double lat = request.getLat();
        double lng = request.getLng();

        List<Stop> todayStops = liveStopFinder.getTodayStops(trip);
        Stop current = liveStopFinder.findCurrentStop(todayStops, lat, lng);
        Stop nextStop = liveStopFinder.findNextStop(todayStops, current);
        Stop fixedStop = liveStopFinder.findFixedStop(todayStops, current);
        Map<Long, Route> routeMap = new HashMap<>(); // 칸 id → 앞에서 오는 길 (카드 사이 이동 칩)
        Map<Long, Integer> etaMap = getEtaMap(trip, todayStops, current, lat, lng, routeMap);
        Map<Long, String> reachMap = getReachMap(trip, todayStops, current, fixedStop, lat, lng);
        Set<Long> mealStopIds = new HashSet<>(); // 남은 칸 중 식당·카페·술집 (영업 정보 읽을 때 같이 채움 → 쿼리 추가 없음)
        Map<Long, OpenInfo> openMap = getOpenInfoMap(todayStops, current, etaMap, mealStopIds);
        // 여유(분) = 고정 일정 계획 시각 − 예상 도착. 음수면 지각
        Integer slack = fixedStop == null ? null : Geo.toMinutes(fixedStop.getTime()) - etaMap.get(fixedStop.getId());

        StatusResponse response = new StatusResponse();
        response.setNow(Geo.toHhmm(Geo.nowMinutes()));
        response.setCurrentStopId(current == null ? null : current.getId());
        for (Stop stop : todayStops) {
            OpenInfo open = openMap.get(stop.getId());
            String reach = getReach(stop, current, reachMap, open);
            response.getStops().add(toStatusStop(stop, etaMap.get(stop.getId()), reach, open, routeMap.get(stop.getId()), mealStopIds.contains(stop.getId())));
        }
        response.setNext(getNextRoute(trip, nextStop, lat, lng));
        response.setFixed(toFixedStatus(fixedStop, etaMap, slack));
        response.setHint(getHint(todayStops, fixedStop, slack, reachMap));
        response.setArea(getArea(fixedStop, nextStop, slack, etaMap, lat, lng));

        // 알림은 LiveAlertMaker, 날씨는 LiveWeatherChecker가 (계산 결과를 context 하나로 넘김)
        StatusContext context = toContext(todayStops, current, nextStop, fixedStop, slack, etaMap, openMap);
        context.setFastestSlack(getFastestSlack(fixedStop, slack, lat, lng)); // 어떤 방법으로도 못 닿는지 (lost-·늦음 카드 문구)
        context.setFixedMeal(fixedStop != null && mealStopIds.contains(fixedStop.getId())); // 놓침 문구: 식당이면 "예약 시각", 아니면 "약속 시각" (11/12)
        context.setWeather(liveWeatherChecker.check(lat, lng, context)); // 알림(rain-)도 이 값을 씀
        response.setWeather(context.getWeather());
        LeaveInfo leave = liveAlertMaker.makeLeave(context);
        response.setLeave(leave);
        LiveAlert alert = liveAlertMaker.makeAlert(context, leave);
        response.setAlert(alert);
        response.setAdjust(autoAdjuster.getRecent(tripId));
        sendAlertNotice(memberId, tripId, alert);

        // 7단계: 다시 짜는 게 좋은지 (알림과 같은 기준이라 LiveAlertMaker가 판단)
        response.setReplan(liveAlertMaker.isReplanNeeded(context));
        return response;
    }

    // ---------------------------------------------------------------- 지금 상황(status) 도우미

    /** 맨 위 알림을 알림함에도 남김. 같은 알림은 ①이 noticeKey로 한 번만 저장 */
    private void sendAlertNotice(long memberId, long tripId, LiveAlert alert) {
        if (alert == null) {
            return;
        }
        Notification notice = new Notification();
        notice.setMemberId(memberId);
        notice.setKind(alert.getKey().startsWith("rain-") ? "info" : "trip");
        notice.setLevel(alert.getLevel());
        notice.setTitle(alert.getTitle());
        notice.setText(alert.getText());
        notice.setLink("/live?id=" + tripId); // 어느 여행 알림인지 (여행 중 화면은 id를 안 봐도 됨, 시연 연습 알림 숨기기에 씀, 11/06)
        notice.setNoticeKey("trip" + tripId + "-" + Geo.now().toLocalDate() + "-" + toNoticeKey(alert.getKey()));
        notificationService.send(notice);
    }

    /** 알림 키에서 알림함용 키 만들기: 1분·5분마다 끝 숫자가 바뀌는 출발·더 머묾·지각 알림은 "앞부분-칸 id"만 써서 칸마다 한 번만 */
    private static String toNoticeKey(String alertKey) {
        boolean isCounting = alertKey.startsWith("leave-") || alertKey.startsWith("overdue-") || alertKey.startsWith("late-");
        if (!isCounting) {
            return alertKey;
        }
        String[] parts = alertKey.split("-"); // 예: leave-12-9 → [leave, 12, 9]
        return parts[0] + "-" + parts[1];
    }

    /** 남은 칸마다 예상 도착 시각(자정부터 센 분). key = 칸 id. 앞 칸 출발 시각에 이동 시간을 차례로 더해 감. 구간 길은 routeMap에 담음 */
    private Map<Long, Integer> getEtaMap(Trip trip, List<Stop> todayStops, Stop current, double lat, double lng,
                                         Map<Long, Route> routeMap) {
        Map<Long, Integer> etaMap = new HashMap<>();
        // 출발점: 지금 어떤 칸에 있으면 그 칸에서 (머무는 시간이 끝나면 출발), 이동 중이면 내 위치에서 지금 출발
        double fromLat = lat;
        double fromLng = lng;
        int clock = Geo.nowMinutes();
        if (current != null) {
            fromLat = current.getLat();
            fromLng = current.getLng();
            clock = Math.max(clock, liveStopFinder.getLeaveMinutes(current));
        }
        int routeCount = 0;
        for (Stop stop : todayStops) {
            if (!liveStopFinder.isRemaining(stop, current)) {
                continue;
            }
            // 가까운 몇 구간만 진짜 길찾기 API, 먼 구간은 거리로 추정 (API 무료 한도 아끼기)
            boolean isPrecise = routeCount < PRECISE_ROUTE_COUNT;
            Route route = routeClient.getRoute(fromLat, fromLng, stop.getLat(), stop.getLng(), stop.getMode(), trip.getTransports(), isPrecise);
            int arrive = clock + route.getMinutes();
            etaMap.put(stop.getId(), arrive);
            routeMap.put(stop.getId(), route);

            clock = liveStopFinder.getStartMinutes(stop, arrive) + liveStopFinder.getStay(stop);
            fromLat = stop.getLat();
            fromLng = stop.getLng();
            routeCount++;
        }
        return etaMap;
    }

    /**
     * 고정 일정 전의 남은 칸마다 "들렀다가 고정 일정에 맞출 수 있나". key = 칸 id
     * ok: 내 위치 → 그 칸 → 머묾 → 고정 일정이 남은 시간 안 / tight: 머묾을 절반으로 줄이면 안 / no: 그래도 안 됨
     */
    private Map<Long, String> getReachMap(Trip trip, List<Stop> todayStops, Stop current, Stop fixedStop, double lat, double lng) {
        Map<Long, String> reachMap = new HashMap<>();
        if (fixedStop == null) {
            return reachMap;
        }
        int fixedMinutes = Geo.toMinutes(fixedStop.getTime());
        int available = fixedMinutes - Geo.nowMinutes();
        for (Stop stop : todayStops) {
            boolean isBeforeFixed = Geo.toMinutes(stop.getTime()) <= fixedMinutes;
            if (!liveStopFinder.isRemaining(stop, current) || stop == fixedStop || !isBeforeFixed) {
                continue;
            }
            // 후보를 여러 개 비교하니 거리로 추정 (isPrecise=false)
            int go = routeClient.getRoute(lat, lng, stop.getLat(), stop.getLng(), stop.getMode(), trip.getTransports(), false).getMinutes();
            int back = routeClient.getRoute(stop.getLat(), stop.getLng(), fixedStop.getLat(), fixedStop.getLng(),
                    fixedStop.getMode(), trip.getTransports(), false).getMinutes();
            reachMap.put(stop.getId(), judgeReach(go, liveStopFinder.getStay(stop), back, available));
        }
        return reachMap;
    }

    /** 가기 + 머묾 + 고정 일정으로 가기가 남은 시간 안이면 ok, 머묾 절반이면 tight, 아니면 no */
    private String judgeReach(int go, int stay, int back, int available) {
        if (go + stay + back <= available) {
            return "ok";
        }
        if (go + stay / 2 + back <= available) {
            return "tight";
        }
        return "no";
    }

    /** 고정 일정 정보 (없으면 null) */
    private FixedStatus toFixedStatus(Stop fixedStop, Map<Long, Integer> etaMap, Integer slack) {
        if (fixedStop == null) {
            return null;
        }
        FixedStatus fixed = new FixedStatus();
        fixed.setId(fixedStop.getId());
        fixed.setName(fixedStop.getName());
        fixed.setTime(fixedStop.getTime());
        fixed.setLat(fixedStop.getLat());
        fixed.setLng(fixedStop.getLng());
        fixed.setEta(Geo.toHhmm(etaMap.get(fixedStop.getId())));
        fixed.setSlackMin(slack);
        return fixed;
    }

    /** 고정 일정에 늦을 때만 조언 한 줄 (그대로 갈 수 있는 곳 / 시간만 줄이면 되는 곳) */
    private String getHint(List<Stop> todayStops, Stop fixedStop, Integer slack, Map<Long, String> reachMap) {
        if (slack == null || slack >= 0) {
            return null;
        }
        String okName = null;
        String tightName = null;
        for (Stop stop : todayStops) {
            String reach = reachMap.get(stop.getId());
            if (okName == null && "ok".equals(reach)) {
                okName = stop.getName();
            }
            if (tightName == null && "tight".equals(reach)) {
                tightName = stop.getName();
            }
        }
        if (tightName != null) {
            String okPart = okName == null ? "" : okName + "은(는) 그대로 갈 수 있어요! ";
            return okPart + tightName + " 시간만 줄이면 " + fixedStop.getName() + "에 맞출 수 있어요.";
        }
        if (okName != null) {
            return okName + "은(는) 그대로 갈 수 있어요. 한 곳을 빼면 " + fixedStop.getName() + "에 맞출 수 있어요.";
        }
        return null;
    }

    /** '지금 갈 수 있는 구역' 기준: 고정 일정(없으면 다음 일정)까지 여유. 남은 일정이 없으면 null */
    private AreaInfo getArea(Stop fixedStop, Stop nextStop, Integer slack, Map<Long, Integer> etaMap, double lat, double lng) {
        Stop basis = fixedStop != null ? fixedStop : nextStop;
        if (basis == null) {
            return null;
        }
        AreaInfo area = new AreaInfo();
        if (fixedStop != null) {
            area.setSlackMin(slack);
        } else {
            area.setSlackMin(Geo.toMinutes(nextStop.getTime()) - etaMap.get(nextStop.getId()));
        }
        area.setBasis(basis.getName());
        area.setBasisTime(basis.getTime());
        area.setLat(lat);
        area.setLng(lng);
        return area;
    }

    /** 내 위치 → 다음 남은 칸 길찾기 (남은 칸이 없으면 null) */
    private NextRoute getNextRoute(Trip trip, Stop nextStop, double lat, double lng) {
        if (nextStop == null) {
            return null;
        }
        Route route = routeClient.getRoute(lat, lng, nextStop.getLat(), nextStop.getLng(), nextStop.getMode(), trip.getTransports(), true);
        NextRoute next = new NextRoute();
        next.setId(nextStop.getId());
        next.setMinutes(route.getMinutes());
        if (route.getCoords() != null) {
            next.setCoords(route.getCoords());
        }
        next.setSource(route.getSource());
        return next;
    }

    /** Stop → 화면용 StatusStop. eta: 남은 칸이면 예상 도착, 아니면 계획 시각. open: 영업 정보, route: 앞에서 오는 길 (둘 다 없으면 null) */
    private StatusStop toStatusStop(Stop stop, Integer eta, String reach, OpenInfo open, Route route, boolean isMeal) {
        StatusStop status = new StatusStop();
        status.setId(stop.getId());
        status.setPlaceId(stop.getPlaceId());
        status.setName(stop.getName());
        status.setCategory(stop.getCategory());
        status.setLat(stop.getLat());
        status.setLng(stop.getLng());
        status.setTime(stop.getTime());
        status.setStay(stop.getStay());
        status.setFixed(stop.getFixed());
        status.setEta(eta == null ? stop.getTime() : Geo.toHhmm(eta));
        status.setReach(reach);
        status.setImportance(stop.getImportance());
        status.setMeal(isMeal);
        status.setClosingSoon(false);
        if (route != null) {
            status.setMoveMin(route.getMinutes());
            status.setMoveMode(route.getMode());
            status.setMoveMeters(route.getMeters());
        }
        if (open != null) {
            status.setCloseAt(open.getCloseAt());
            status.setClosedReason(open.getClosedReason());
            status.setClosingSoon(Boolean.TRUE.equals(open.getClosingSoon()));
        }
        return status;
    }

    /**
     * 칸 상태 (위에서부터 먼저 맞는 것): done(다녀옴·뺌) / here(지금 있는 곳) / closed(도착할 때 닫힘) /
     * missed(계획 시간 지남) / fixed(고정 일정) / ok·tight·no(reachMap)
     */
    private String getReach(Stop stop, Stop current, Map<Long, String> reachMap, OpenInfo open) {
        if (!liveStopFinder.isTodo(stop)) {
            return "done";
        }
        if (stop == current) {
            return "here";
        }
        if (isClosed(open)) {
            return "closed";
        }
        if (liveStopFinder.isMissed(stop)) {
            return "missed";
        }
        if (Boolean.TRUE.equals(stop.getFixed())) {
            return "fixed";
        }
        return reachMap.getOrDefault(stop.getId(), "ok");
    }

    // ---------------------------------------------------------------- 영업 정보 (5단계)

    /** 남은 칸마다 예상 도착 시각 기준 영업 정보. key = 칸 id. DB에 없는 장소(placeId 없음)는 모름이라 빠짐 */
    private Map<Long, OpenInfo> getOpenInfoMap(List<Stop> todayStops, Stop current, Map<Long, Integer> etaMap, Set<Long> mealStopIds) {
        Map<Long, OpenInfo> openMap = new HashMap<>();
        LocalDate today = Geo.now().toLocalDate();
        for (Stop stop : todayStops) {
            if (!liveStopFinder.isRemaining(stop, current) || stop.getPlaceId() == null) {
                continue;
            }
            Place place = findPlace(stop.getPlaceId());
            if (place != null) {
                openMap.put(stop.getId(), placeService.getOpenInfo(place, today, etaMap.get(stop.getId()), liveStopFinder.getStay(stop)));
                if (MEAL_KINDS.contains(place.getKind())) {
                    mealStopIds.add(stop.getId()); // 11/12: 놓침 카드·"못 가게 됐어요" 버튼은 식당·카페·술집만 (KTX 등은 빠짐)
                }
            }
        }
        return openMap;
    }

    /** 고정 일정에 늦을 때: 가장 빠른 방법으로 지금 바로 가도 남는 여유(분). 안 늦으면 null (11/12 ④) */
    private Integer getFastestSlack(Stop fixedStop, Integer slack, double lat, double lng) {
        if (slack == null || slack >= 0) {
            return null; // 안 늦으면 계산 안 함 (TMAP 호출 줄이기)
        }
        String cacheKey = fixedStop.getId() + "-" + (-slack) / LATE_STEP_MIN;
        Integer cached = fastestSlackCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        int fastestMinutes = transferFinder.findFastestMinutes(lat, lng, fixedStop.getLat(), fixedStop.getLng());
        int fastestSlack = Geo.toMinutes(fixedStop.getTime()) - (Geo.nowMinutes() + fastestMinutes);
        fastestSlackCache.put(cacheKey, fastestSlack);
        return fastestSlack;
    }

    /** 장소 하나 (지워진 장소면 404 대신 null → 영업 정보 없이 계속) */
    private Place findPlace(String placeId) {
        try {
            return placeService.getPlace(placeId);
        } catch (ApiException e) {
            return null;
        }
    }

    /** 도착할 때 닫혀 있나 (휴무 포함). 영업시간을 모르면 false */
    private boolean isClosed(OpenInfo open) {
        return open != null && Boolean.FALSE.equals(open.getOpen());
    }

    // ---------------------------------------------------------------- 알림에 넘길 계산 결과 (4·5단계)

    /** 계산 결과를 StatusContext 하나에 담기 → LiveAlertMaker가 읽어서 알림을 만듦 */
    private StatusContext toContext(List<Stop> todayStops, Stop current, Stop nextStop, Stop fixedStop, Integer slack,
                                    Map<Long, Integer> etaMap, Map<Long, OpenInfo> openMap) {
        StatusContext context = new StatusContext();
        context.setNow(Geo.nowMinutes());
        context.setTodayStops(todayStops);
        context.setCurrent(current);
        context.setLeaveAt(current == null ? null : liveStopFinder.getLeaveMinutes(current));
        context.setNextStop(nextStop);
        context.setFixedStop(fixedStop);
        context.setSlack(slack);
        context.setMissedStops(findMissedStops(todayStops, current));
        context.setEtaMap(etaMap);
        context.setOpenMap(openMap);
        return context;
    }

    /** 남은 칸 중 지난 일정 (순서대로) */
    private List<Stop> findMissedStops(List<Stop> todayStops, Stop current) {
        List<Stop> missedStops = new ArrayList<>();
        for (Stop stop : todayStops) {
            if (liveStopFinder.isRemaining(stop, current) && liveStopFinder.isMissed(stop)) {
                missedStops.add(stop);
            }
        }
        return missedStops;
    }
}
