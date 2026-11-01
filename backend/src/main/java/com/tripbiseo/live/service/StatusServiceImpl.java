package com.tripbiseo.live.service;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.tripbiseo.live.dto.StatusResponse;
import com.tripbiseo.live.dto.StatusStop;
import com.tripbiseo.trip.dto.OpenInfo;
import com.tripbiseo.trip.dto.Place;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;
import com.tripbiseo.trip.service.PlaceService;
import com.tripbiseo.trip.service.TripService;

import lombok.RequiredArgsConstructor;

/**
 * ④ 지금 상황 다시 계산 (POST /live/status). LiveServiceImpl이 너무 커져서 나눔.
 * 예전 코드: git show backend-v0:backend/src/main/java/com/tripbiseo/service/LiveService.java (status/today/start/alert/rain)
 */
@Service
@RequiredArgsConstructor
public class StatusServiceImpl implements StatusService {

    private static final int ARRIVE_RADIUS_M = 150;     // GPS 도착 기록이 없어도 이 안이면 "지금 있는 곳"
    private static final int PRECISE_ROUTE_COUNT = 3;   // 가까운 3구간만 진짜 길찾기, 나머지는 거리로 추정
    private static final int LEAVE_WINDOW_MIN = 10;     // 출발 시각 앞뒤 10분 안이면 출발 알림
    private static final int TIGHT_SLACK_MIN = 10;      // 고정 일정 여유가 이 이하면 "여유가 얼마 없어요"
    private static final int NEXT_LATE_MIN = 10;        // 다음 장소에 이보다 더 늦으면 알림

    private final TripService tripService;
    private final PlaceService placeService; // 장소 영업 정보 (②)
    private final RouteClient routeClient;   // 이동 시간 (후보 비교는 isPrecise=false)
    // TODO: ④ 6단계 WeatherClient(날씨)가 필요해지면 여기에 추가

    /** 지금 상황 다시 계산 */
    // TODO: ④ 지금 상황. 공통 오류 → 오늘(day)·남은 일정 → 칸마다 eta·reach·영업 정보 → alert 하나(우선순위) → leave·fixed·hint·replan·area·next.
    //       참고: 예전 LiveService.status/today/start/alert/rain.
    //       주의: 날씨는 weatherClient.getForecast(서버가 위치로 직접), 실패하면 weather = null. 구글 검색 대체는 쓰지 않기
    @Override
    public StatusResponse getStatus(long memberId, long tripId, LocationRequest request) {
        // 1단계: now, currentStopId, stops[] / 2단계: eta, next / 3단계: fixed, reach, hint, area
        // 4단계: leave, alert / 5단계: 영업 정보
        if (request.getLat() == null || request.getLng() == null) {
            throw fail(400, "내 위치를 먼저 찾아 주세요.");
        }
        double lat = request.getLat();
        double lng = request.getLng();
        Trip trip = tripService.getTrip(memberId, tripId);
        // TODO: ④ 404·ongoing 검사는 ②의 진짜 getTrip이 나오면 추가

        List<Stop> todayStops = getTodayStops(trip);
        Stop current = findCurrentStop(todayStops, lat, lng);
        Stop nextStop = findNextStop(todayStops, current);
        Stop fixedStop = findFixedStop(todayStops, current);
        Map<Long, Integer> etaMap = getEtaMap(trip, todayStops, current, lat, lng);
        Map<Long, String> reachMap = getReachMap(trip, todayStops, current, fixedStop, lat, lng);
        Map<Long, OpenInfo> openMap = getOpenInfoMap(todayStops, current, etaMap);
        // 여유(분) = 고정 일정 계획 시각 − 예상 도착. 음수면 지각
        Integer slack = fixedStop == null ? null : Geo.toMinutes(fixedStop.getTime()) - etaMap.get(fixedStop.getId());

        StatusResponse response = new StatusResponse();
        response.setNow(Geo.toHhmm(Geo.nowMinutes()));
        response.setCurrentStopId(current == null ? null : current.getId());
        for (Stop stop : todayStops) {
            OpenInfo open = openMap.get(stop.getId());
            String reach = getReach(stop, current, reachMap, open);
            response.getStops().add(toStatusStop(stop, etaMap.get(stop.getId()), reach, open));
        }
        response.setNext(getNextRoute(trip, nextStop, lat, lng));
        response.setFixed(toFixedStatus(fixedStop, etaMap, slack));
        response.setHint(getHint(todayStops, fixedStop, slack, reachMap));
        response.setArea(getArea(fixedStop, nextStop, slack, etaMap, lat, lng));
        LeaveInfo leave = getLeave(current, nextStop, etaMap);
        response.setLeave(leave);
        LiveAlert closedAlert = getClosedAlert(todayStops, current, etaMap, openMap);
        response.setAlert(getAlert(todayStops, current, nextStop, fixedStop, slack, etaMap, leave, closedAlert));

        // TODO: ④ 7단계에서 진짜 값으로 (지금은 가짜 값)
        response.setReplan(false);
        return response;
    }

    // ---------------------------------------------------------------- 지금 상황(status) 도우미

    /** 오늘 칸 전부 (다녀온 칸·뺀 칸 포함, 화면에 오늘 일정을 다 보여 줘야 해서) */
    private List<Stop> getTodayStops(Trip trip) {
        // ChronoUnit.DAYS.between: 두 날짜 사이가 며칠인지 (시작일이면 0 → 1일째)
        long today = ChronoUnit.DAYS.between(LocalDate.parse(trip.getStartDate()), Geo.now().toLocalDate()) + 1;
        List<Stop> stops = new ArrayList<>();
        for (Stop stop : trip.getStops()) {
            if (stop.getDay() != null && stop.getDay() == today) {
                stops.add(stop);
            }
        }
        return stops;
    }

    /** 지금 있는 칸: 도착했고 아직 출발 안 한 칸, 없으면 150m 안에서 제일 가까운 todo 칸 (없으면 null) */
    private Stop findCurrentStop(List<Stop> stops, double lat, double lng) {
        Stop nearest = null;
        double nearestMeters = ARRIVE_RADIUS_M;
        for (Stop stop : stops) {
            if (!isTodo(stop) || stop.getLat() == null || stop.getLng() == null) {
                continue;
            }
            if (stop.getArrivedAt() != null && stop.getDepartedAt() == null) {
                return stop; // GPS로 도착 기록된 칸이 제일 확실함
            }
            double meters = Geo.distanceMeters(lat, lng, stop.getLat(), stop.getLng());
            if (meters <= nearestMeters) {
                nearest = stop;
                nearestMeters = meters;
            }
        }
        return nearest;
    }

    /** 남은 칸마다 예상 도착 시각(자정부터 센 분). key = 칸 id. 앞 칸 출발 시각에 이동 시간을 차례로 더해 감 */
    private Map<Long, Integer> getEtaMap(Trip trip, List<Stop> todayStops, Stop current, double lat, double lng) {
        Map<Long, Integer> etaMap = new HashMap<>();
        // 출발점: 지금 어떤 칸에 있으면 그 칸에서 (머무는 시간이 끝나면 출발), 이동 중이면 내 위치에서 지금 출발
        double fromLat = lat;
        double fromLng = lng;
        int clock = Geo.nowMinutes();
        if (current != null) {
            fromLat = current.getLat();
            fromLng = current.getLng();
            clock = Math.max(clock, getLeaveMinutes(current));
        }
        int routeCount = 0;
        for (Stop stop : todayStops) {
            if (!isRemaining(stop, current)) {
                continue;
            }
            // 가까운 몇 구간만 진짜 길찾기 API, 먼 구간은 거리로 추정 (API 무료 한도 아끼기)
            boolean isPrecise = routeCount < PRECISE_ROUTE_COUNT;
            Route route = routeClient.getRoute(fromLat, fromLng, stop.getLat(), stop.getLng(), stop.getMode(), trip.getTransports(), isPrecise);
            int arrive = clock + route.getMinutes();
            etaMap.put(stop.getId(), arrive);

            clock = getStartMinutes(stop, arrive) + getStay(stop);
            fromLat = stop.getLat();
            fromLng = stop.getLng();
            routeCount++;
        }
        return etaMap;
    }

    /** 다음 남은 칸 (없으면 null) */
    private Stop findNextStop(List<Stop> todayStops, Stop current) {
        for (Stop stop : todayStops) {
            if (isRemaining(stop, current)) {
                return stop;
            }
        }
        return null;
    }

    /** 남은 칸 중 첫 고정 일정 (없으면 null) */
    private Stop findFixedStop(List<Stop> todayStops, Stop current) {
        for (Stop stop : todayStops) {
            if (isRemaining(stop, current) && Boolean.TRUE.equals(stop.getFixed())) {
                return stop;
            }
        }
        return null;
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
            if (!isRemaining(stop, current) || stop == fixedStop || !isBeforeFixed) {
                continue;
            }
            // 후보를 여러 개 비교하니 거리로 추정 (isPrecise=false)
            int go = routeClient.getRoute(lat, lng, stop.getLat(), stop.getLng(), stop.getMode(), trip.getTransports(), false).getMinutes();
            int back = routeClient.getRoute(stop.getLat(), stop.getLng(), fixedStop.getLat(), fixedStop.getLng(),
                    fixedStop.getMode(), trip.getTransports(), false).getMinutes();
            reachMap.put(stop.getId(), judgeReach(go, getStay(stop), back, available));
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

    /** 남은 일정인지: 아직 갈 칸 + 지금 있는 칸이 아님 + 좌표 있음 (API.md "남은 일정") */
    private boolean isRemaining(Stop stop, Stop current) {
        return isTodo(stop) && stop != current && stop.getLat() != null && stop.getLng() != null;
    }

    /** 지금 칸에서 떠날 시각(분) = 도착 시각 + 머무는 시간. 도착 기록이 없으면 계획 시각으로 */
    private int getLeaveMinutes(Stop current) {
        int arrived = Geo.toMinutes(current.getTime());
        if (current.getArrivedAt() != null && current.getArrivedAt().length() >= 16) {
            // "2026-11-01T13:30:00"에서 "13:30" 부분만
            arrived = Geo.toMinutes(current.getArrivedAt().substring(11, 16));
        }
        return arrived + getStay(current);
    }

    /** 칸에서 시작하는 시각: 고정 일정은 일찍 도착해도 계획 시각에 시작 */
    private int getStartMinutes(Stop stop, int arrive) {
        if (Boolean.TRUE.equals(stop.getFixed())) {
            return Math.max(arrive, Geo.toMinutes(stop.getTime()));
        }
        return arrive;
    }

    /** 머무는 시간(분). 없으면 0 */
    private int getStay(Stop stop) {
        return stop.getStay() == null ? 0 : stop.getStay();
    }

    /** Stop → 화면용 StatusStop. eta: 남은 칸이면 예상 도착, 아니면 계획 시각. open: 영업 정보 (없으면 null) */
    private StatusStop toStatusStop(Stop stop, Integer eta, String reach, OpenInfo open) {
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
        status.setClosingSoon(false);
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
        if (!isTodo(stop)) {
            return "done";
        }
        if (stop == current) {
            return "here";
        }
        if (isClosed(open)) {
            return "closed";
        }
        if (isMissed(stop)) {
            return "missed";
        }
        if (Boolean.TRUE.equals(stop.getFixed())) {
            return "fixed";
        }
        return reachMap.getOrDefault(stop.getId(), "ok");
    }

    /** 지난 일정: 고정 일정이 아니고, 도착 기록이 없는데 계획한 머무름까지 이미 끝났어야 하는 칸 */
    private boolean isMissed(Stop stop) {
        if (Boolean.TRUE.equals(stop.getFixed()) || stop.getArrivedAt() != null) {
            return false;
        }
        int plannedEnd = Geo.toMinutes(stop.getTime()) + getStay(stop);
        return plannedEnd < Geo.nowMinutes();
    }

    /** 아직 갈 칸인지 (done·skipped가 아님) */
    private boolean isTodo(Stop stop) {
        return "todo".equals(stop.getState());
    }

    // ---------------------------------------------------------------- 영업 정보 (5단계)

    /** 남은 칸마다 예상 도착 시각 기준 영업 정보. key = 칸 id. DB에 없는 장소(placeId 없음)는 모름이라 빠짐 */
    private Map<Long, OpenInfo> getOpenInfoMap(List<Stop> todayStops, Stop current, Map<Long, Integer> etaMap) {
        Map<Long, OpenInfo> openMap = new HashMap<>();
        LocalDate today = Geo.now().toLocalDate();
        for (Stop stop : todayStops) {
            if (!isRemaining(stop, current) || stop.getPlaceId() == null) {
                continue;
            }
            Place place = findPlace(stop.getPlaceId());
            if (place != null) {
                openMap.put(stop.getId(), placeService.getOpenInfo(place, today, etaMap.get(stop.getId()), getStay(stop)));
            }
        }
        return openMap;
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

    /** 열려 있지만 닫을 때까지 남은 시간이 머무는 시간보다 짧은가 */
    private boolean isTooShort(OpenInfo open, Stop stop) {
        boolean isOpen = open != null && Boolean.TRUE.equals(open.getOpen());
        return isOpen && open.getMinutesLeft() != null && open.getMinutesLeft() < getStay(stop);
    }

    /**
     * 영업 알림 (남은 칸 중 첫 번째 하나, 없으면 null)
     *  - 도착할 때 닫힘·휴무: danger / 열려 있지만 머무는 시간을 다 못 채움: warn
     */
    private LiveAlert getClosedAlert(List<Stop> todayStops, Stop current, Map<Long, Integer> etaMap, Map<Long, OpenInfo> openMap) {
        for (Stop stop : todayStops) {
            OpenInfo open = openMap.get(stop.getId());
            if (isClosed(open)) {
                String reason = open.getClosedReason() == null ? "도착할 때 문을 닫아요" : open.getClosedReason();
                return makeAlert("closed-" + stop.getId(), "danger", stop.getName() + ": " + reason,
                        "도착 예상 " + Geo.toHhmm(etaMap.get(stop.getId())) + " — '대안 보기'로 다른 곳을 찾아보세요");
            }
            if (isTooShort(open, stop)) {
                return makeAlert("closed-" + stop.getId() + "-short", "warn", stop.getName() + ": " + open.getCloseAt() + "에 닫아요",
                        "도착 후 " + open.getMinutesLeft() + "분밖에 못 있어요. '대안 보기'로 다른 곳을 찾아보세요");
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 출발·지각 알림 (4단계)

    /**
     * 출발 알림: 출발 시각 앞뒤 10분 안일 때만 (아니면 null)
     *  - 지금 어떤 칸에 있으면: 그 칸에서 떠날 시각 (도착 + 머묾)
     *  - 이동 중이면: 다음 칸에 계획 시각대로 닿으려면 떠나야 하는 시각 (계획 시각 − 이동 시간)
     */
    private LeaveInfo getLeave(Stop current, Stop nextStop, Map<Long, Integer> etaMap) {
        int now = Geo.nowMinutes();
        if (current != null) {
            int leaveAt = getLeaveMinutes(current);
            String to = nextStop == null ? null : nextStop.getName();
            return makeLeave(current, leaveAt, leaveAt - now, to, true);
        }
        if (nextStop == null || isMissed(nextStop)) {
            return null;
        }
        // 이동 중이면 eta = 지금 + 이동 시간이라, 이동 시간 = eta − 지금
        int moveMinutes = etaMap.get(nextStop.getId()) - now;
        int leaveAt = Geo.toMinutes(nextStop.getTime()) - moveMinutes;
        return makeLeave(nextStop, leaveAt, leaveAt - now, nextStop.getName(), false);
    }

    /** 남은 분이 −10~10 안이면 LeaveInfo, 아니면 null */
    private LeaveInfo makeLeave(Stop stop, int leaveAt, int minutesLeft, String to, boolean isAtStop) {
        if (Math.abs(minutesLeft) > LEAVE_WINDOW_MIN) {
            return null;
        }
        LeaveInfo leave = new LeaveInfo();
        leave.setStopId(stop.getId());
        leave.setName(stop.getName());
        leave.setLeaveAt(Geo.toHhmm(leaveAt));
        leave.setMinutesLeft(minutesLeft);
        leave.setTo(to);
        leave.setAtStop(isAtStop);
        return leave;
    }

    /**
     * 화면 맨 위 알림 하나. 여러 개면 위에서부터 먼저 맞는 것 하나만:
     * 더 머묾 > 영업 끝·휴무·머묾 부족 > 고정 일정 지각 > 출발 > 지난 일정 > 고정 여유 10분 이하 > 다음 장소 지각
     */
    private LiveAlert getAlert(List<Stop> todayStops, Stop current, Stop nextStop, Stop fixedStop, Integer slack,
                               Map<Long, Integer> etaMap, LeaveInfo leave, LiveAlert closedAlert) {
        int now = Geo.nowMinutes();
        if (current != null && now >= getLeaveMinutes(current)) {
            int overMinutes = now - getLeaveMinutes(current);
            // key 끝의 숫자는 5분마다 바뀜 → 화면이 5분마다 다시 알려 줌
            return makeAlert("overdue-" + current.getId() + "-" + overMinutes / 5, "danger",
                    current.getName() + "에서 " + overMinutes + "분 더 머물렀어요", "계획한 출발 시각이 지났어요. 지금 위치에서 다시 계산했어요");
        }
        if (closedAlert != null) {
            return closedAlert;
        }
        if (slack != null && slack < 0) {
            // key 끝의 숫자는 10분 더 늦어질 때마다 바뀜 → 그때마다 다시 알려 줌
            return makeAlert("late-" + fixedStop.getId() + "-" + (-slack) / 10, "danger",
                    "지금대로면 " + fixedStop.getName() + "에 " + (-slack) + "분 늦어요", "'대안 보기'로 늦지 않는 방법을 확인하세요");
        }
        if (leave != null) {
            return makeLeaveAlert(leave);
        }
        Stop missed = findMissedStop(todayStops, current);
        if (missed != null) {
            return makeAlert("missed-" + missed.getId(), "warn",
                    missed.getName() + " 계획 시간이 지났어요", "아직 안 갔다면 '대안 보기'에서 빼거나 바꿀 수 있어요");
        }
        if (slack != null && slack <= TIGHT_SLACK_MIN) {
            return makeAlert("tight-" + fixedStop.getId(), "warn",
                    fixedStop.getName() + "까지 여유가 " + slack + "분뿐이에요", "다음 장소는 짧게 들르는 게 좋아요");
        }
        return makeNextLateAlert(nextStop, etaMap);
    }

    /** 출발 알림: 남은 분을 key에 넣어 1분마다 새로 울리게 함 (repeat = true) */
    private LiveAlert makeLeaveAlert(LeaveInfo leave) {
        int left = leave.getMinutesLeft();
        String title = left + "분 뒤 출발해야 해요";
        if (left == 0) {
            title = "지금 출발해야 해요";
        } else if (left < 0) {
            title = "출발 시각이 " + (-left) + "분 지났어요";
        }
        String text = leave.getName() + "에 계획대로 닿으려면 " + leave.getLeaveAt() + "에 출발";
        if (leave.isAtStop()) {
            text = leave.getName() + " 출발 " + leave.getLeaveAt() + (leave.getTo() == null ? "" : " → " + leave.getTo());
        }
        LiveAlert alert = makeAlert("leave-" + leave.getStopId() + "-" + left, left > 0 ? "warn" : "danger", title, text);
        alert.setRepeat(true);
        return alert;
    }

    /** 다음 장소에 10분 넘게 늦으면 알림 (아니면 null) */
    private LiveAlert makeNextLateAlert(Stop nextStop, Map<Long, Integer> etaMap) {
        if (nextStop == null) {
            return null;
        }
        int lateMinutes = etaMap.get(nextStop.getId()) - Geo.toMinutes(nextStop.getTime());
        if (lateMinutes <= NEXT_LATE_MIN) {
            return null;
        }
        return makeAlert("next-" + nextStop.getId() + "-" + lateMinutes / 10, "warn",
                nextStop.getName() + "에 " + lateMinutes + "분 늦게 도착할 것 같아요", "'대안 보기'로 남은 일정을 다시 짤 수 있어요");
    }

    /** 남은 칸 중 첫 지난 일정 (없으면 null) */
    private Stop findMissedStop(List<Stop> todayStops, Stop current) {
        for (Stop stop : todayStops) {
            if (isRemaining(stop, current) && isMissed(stop)) {
                return stop;
            }
        }
        return null;
    }

    /** 알림 하나 만들기 */
    private LiveAlert makeAlert(String key, String level, String title, String text) {
        LiveAlert alert = new LiveAlert();
        alert.setKey(key);
        alert.setLevel(level);
        alert.setTitle(title);
        alert.setText(text);
        return alert;
    }
}
