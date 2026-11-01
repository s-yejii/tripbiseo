package com.tripbiseo.live.service;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripbiseo.common.ai.AiService;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.common.external.Route;
import com.tripbiseo.common.external.RouteClient;
import com.tripbiseo.common.external.WeatherClient;
import com.tripbiseo.live.dto.AltStop;
import com.tripbiseo.live.dto.Alternative;
import com.tripbiseo.live.dto.AlternativesRequest;
import com.tripbiseo.live.dto.AlternativesResponse;
import com.tripbiseo.live.dto.AppliedStop;
import com.tripbiseo.live.dto.ApplyRequest;
import com.tripbiseo.live.dto.ApplyResponse;
import com.tripbiseo.live.dto.AreaInfo;
import com.tripbiseo.live.dto.AssistRequest;
import com.tripbiseo.live.dto.AssistResponse;
import com.tripbiseo.live.dto.FixedStatus;
import com.tripbiseo.live.dto.InsertRequest;
import com.tripbiseo.live.dto.InsertResponse;
import com.tripbiseo.live.dto.LeaveInfo;
import com.tripbiseo.live.dto.LiveAlert;
import com.tripbiseo.live.dto.LocationRequest;
import com.tripbiseo.live.dto.NextRoute;
import com.tripbiseo.live.dto.RecommendPlace;
import com.tripbiseo.live.dto.RecommendRequest;
import com.tripbiseo.live.dto.RecommendResponse;
import com.tripbiseo.live.dto.StatusResponse;
import com.tripbiseo.live.dto.StatusStop;
import com.tripbiseo.member.service.NotificationService;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;
import com.tripbiseo.trip.service.PlaceService;
import com.tripbiseo.trip.service.TripService;

import lombok.RequiredArgsConstructor;

/**
 * ④ 여행 중 판별. 지금은 docs/API.md 예시 같은 가짜 값이에요.
 * 예전 코드: git show backend-v0:backend/src/main/java/com/tripbiseo/service/LiveService.java (679줄 → 여기와 TrackService로 나눔)
 * 공통 주의
 *  - 오늘 칸은 tripService.getTrip으로 읽고, 고칠 때도 TripService(createStop·updateStop·skipStops)로. 예전처럼 TripMapper를 직접 쓰지 않기
 *  - 장소 판단은 placeService 메서드로 (예전 PlaceService.openInfo·isIndoor static 호출 대신)
 *  - Gemini 프롬프트에 좌표(위도·경도)를 넣지 않기. 날씨는 aiService.askJsonWithWeather(인자 없는 도구, 위치는 서버만 앎)
 */
@Service
@RequiredArgsConstructor
public class LiveServiceImpl implements LiveService {

    private static final int ARRIVE_RADIUS_M = 150;     // GPS 도착 기록이 없어도 이 안이면 "지금 있는 곳"
    private static final int PRECISE_ROUTE_COUNT = 3;   // 가까운 3구간만 진짜 길찾기, 나머지는 거리로 추정

    private final TripService tripService;                 // TODO: ④ 오늘 칸 읽기·고치기
    private final PlaceService placeService;               // TODO: ④ 영업 정보·근처 장소·실내 판단
    private final RouteClient routeClient;                 // 이동 시간 (대안 후보 비교는 isPrecise=false)
    private final WeatherClient weatherClient;             // TODO: ④ status의 weather
    private final AiService aiService;                     // TODO: ④ assist 분류, alternatives
    private final NotificationService notificationService; // TODO: ④ 지연·출발 알림을 ①의 알림함에도 남길 때

    /** 지금 상황 다시 계산 */
    // TODO: ④ 지금 상황. 공통 오류 → 오늘(day)·남은 일정 → 칸마다 eta·reach·영업 정보 → alert 하나(우선순위) → leave·fixed·hint·replan·area·next.
    //       참고: 예전 LiveService.status/today/start/alert/rain.
    //       주의: 날씨는 weatherClient.getForecast(서버가 위치로 직접), 실패하면 weather = null. 구글 검색 대체는 쓰지 않기
    @Override
    public StatusResponse getStatus(long memberId, long tripId, LocationRequest request) {
        // 1단계: now, currentStopId, stops[] / 2단계: eta, next / 3단계: fixed, reach, hint, area
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
        // 여유(분) = 고정 일정 계획 시각 − 예상 도착. 음수면 지각
        Integer slack = fixedStop == null ? null : Geo.toMinutes(fixedStop.getTime()) - etaMap.get(fixedStop.getId());

        StatusResponse response = new StatusResponse();
        response.setNow(Geo.toHhmm(Geo.nowMinutes()));
        response.setCurrentStopId(current == null ? null : current.getId());
        for (Stop stop : todayStops) {
            String reach = getReach(stop, current, reachMap);
            response.getStops().add(toStatusStop(stop, etaMap.get(stop.getId()), reach));
        }
        response.setNext(getNextRoute(trip, nextStop, lat, lng));
        response.setFixed(toFixedStatus(fixedStop, etaMap, slack));
        response.setHint(getHint(todayStops, fixedStop, slack, reachMap));
        response.setArea(getArea(fixedStop, nextStop, slack, etaMap, lat, lng));

        // TODO: ④ 4~7단계에서 진짜 값으로 (지금은 가짜 값)
        response.setAlert(sampleAlert());
        response.setLeave(sampleLeave());
        response.setReplan(false);
        return response;
    }

    /** 여행 중 채팅: 말 분류 */
    // TODO: ④ 채팅. message 1~300자 → aiService.askJson(분류 프롬프트, 분류 DTO) → recommend면 ask-radius, change면 makeAlternatives(reason "request"), 그 밖은 text.
    //       참고: 예전 LiveService.assist. 주의: Gemini 실패(ApiException)는 catch해서 낱말 규칙으로 (오류 내지 않기). 프롬프트에 좌표 넣지 않기.
    //       확인 필요(④): 비 요청일 때도 대안 A·B·C를 계산할지 (API.md)
    @Override
    public AssistResponse assist(long memberId, long tripId, AssistRequest request) {
        String message = request.getMessage() == null ? "" : request.getMessage();
        AssistResponse response = new AssistResponse();
        if (message.contains("추천") || message.contains("카페") || message.contains("맛집")) {
            response.setKind("ask-radius");
            response.setCategory("카페");
        } else if (message.contains("바꿔") || message.contains("늦")) {
            response.setKind("alternatives");
            response.setData(sampleAlternatives());
        } else {
            response.setKind("text");
            response.setText("다음 일정은 19:00 세종문화회관이에요. 지금 20분 여유가 있어요.");
        }
        return response;
    }

    /** 반경 안 평점 상위 3곳 */
    // TODO: ④ 추천. radius 없으면 400 "radius 값이 필요해요.", 100~3000 밖이면 400 → placeService.getNearbyPlaceList(PostGIS) →
    //       평점 없는 곳·도착 때 닫힌 곳 빼고(closedSkipped) 보정 평점 순 3개 + 영업 정보. 참고: 예전 LiveService.recommend, PlaceService.recommend
    @Override
    public RecommendResponse recommend(long memberId, long tripId, RecommendRequest request) {
        RecommendPlace place = new RecommendPlace();
        place.setId("naver-2093701183");
        place.setName("카페OO");
        place.setCategory("카페,디저트");
        place.setAddress("서울 종로구 인사동길 12");
        place.setLat(37.5745);
        place.setLng(126.9849);
        place.setRating(4.7);
        place.setReviewCount(320);
        place.setNaverUrl("https://map.naver.com/p/entry/place/2093701183");
        place.setDistance(180);
        place.setOpen(true);
        place.setCloseAt("22:00");
        place.setMinutesLeft(455);
        place.setClosingSoon(false);
        RecommendResponse response = new RecommendResponse();
        response.getPlaces().add(place);
        response.setClosedSkipped(1);
        return response;
    }

    /** 추천 장소를 다음 일정으로 */
    // TODO: ④ 일정에 넣기. placeService.getPlace(없으면 404) → 새 칸(카페 40분, 아니면 60분) tripService.createStop →
    //       남은 칸 시각 다시 계산해 tripService.updateStop, 고정 일정에 늦으면 앞 칸 머무는 시간을 최대 절반까지. 참고: 예전 LiveService.insert.
    //       주의: 다른 번호(②)의 쓰기 메서드를 여러 번 부르니 @Transactional 유지
    @Override
    @Transactional
    public InsertResponse addRecommendedPlace(long memberId, long tripId, InsertRequest request) {
        InsertResponse response = new InsertResponse();
        response.setTrip(tripService.getTrip(memberId, tripId));
        response.setMessage("카페OO을(를) 다음 일정으로 넣었어요. 세종문화회관에는 12분 여유가 있어요.");
        return response;
    }

    /** 대안 A·B·C */
    // TODO: ④ 대안. 남은 일정 없으면 400 "오늘 남은 일정이 없어요." → 규칙 대안(A 시간 단축 / B 한 곳 빼기 / C 장소 변경) →
    //       aiService.isReady()면 askJsonWithWeather로 Gemini 대안, 실패하면 규칙만. 3개 채우고 key A·B·C, recommended 하나.
    //       참고: 예전 LiveService.alternatives/fit/chain/shape/substitute/recommendBest.
    //       주의: 예전 프롬프트의 "위치 (위도, 경도)"는 빼기. 날씨 도구는 인자 없음. 예전 askWithSearch(구글 검색 대체)는 쓰지 않기.
    //             근처 후보는 placeService.getNearbyPlaceList(PostGIS)로
    @Override
    public AlternativesResponse makeAlternatives(long memberId, long tripId, AlternativesRequest request) {
        return sampleAlternatives();
    }

    /** 고른 대안을 일정에 반영 */
    // TODO: ④ 대안 적용. stops 없으면 400 "적용할 대안이 없어요." → id가 남은 칸이면 eta·stay로 updateStop(고정 일정 시각은 그대로),
    //       id가 없으면 createStop, 대안에 없는 남은 칸은 skipStops → 오늘 칸 tag(done/kept/changed/added). 참고: 예전 LiveService.apply/nextAlert.
    //       주의: ②의 쓰기 메서드를 여러 번 부르니 @Transactional 유지
    @Override
    @Transactional
    public ApplyResponse applyAlternative(long memberId, long tripId, ApplyRequest request) {
        ApplyResponse response = new ApplyResponse();
        response.setKey(request.getKey() == null ? "A" : request.getKey());
        response.setMessage("세종문화회관에 늦지 않게 일정을 바꿨어요!");
        response.setNextAlert("청계천에서 출발할 시각(15:07) 10분 전부터 1분마다 알려드릴게요. 늦게 도착하면 도착한 시각 기준으로 다시 맞춰요.");
        response.getStops().add(appliedStop(102L, "13:30", "카페OO", "카페", 55, "done", null));
        response.getStops().add(appliedStop(103L, "14:37", "청계천", "관광지", 30, "changed", "60분 → 30분"));
        response.getStops().add(appliedStop(105L, "19:00", "세종문화회관", "문화시설", 120, "kept", null));
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

    /** Stop → 화면용 StatusStop. eta: 남은 칸이면 예상 도착, 아니면 계획 시각 */
    private StatusStop toStatusStop(Stop stop, Integer eta, String reach) {
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
        status.setClosingSoon(false);  // TODO: ④ 5단계 — 영업 정보
        return status;
    }

    /**
     * 칸 상태 (위에서부터 먼저 맞는 것): done(다녀옴·뺌) / here(지금 있는 곳) / missed(계획 시간 지남) /
     * fixed(고정 일정) / ok·tight·no(reachMap). closed는 5단계
     */
    private String getReach(Stop stop, Stop current, Map<Long, String> reachMap) {
        if (!isTodo(stop)) {
            return "done";
        }
        if (stop == current) {
            return "here";
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

    // ---------------------------------------------------------------- 가짜 값 (진짜 로직을 만들면 지우기)
    private static LiveAlert sampleAlert() {
        LiveAlert alert = new LiveAlert();
        alert.setKey("leave-102-5");
        alert.setLevel("warn");
        alert.setTitle("5분 뒤 출발해야 해요");
        alert.setText("카페OO 출발 14:25 → 청계천");
        alert.setRepeat(true);
        return alert;
    }

    private static LeaveInfo sampleLeave() {
        LeaveInfo leave = new LeaveInfo();
        leave.setStopId(102L);
        leave.setName("카페OO");
        leave.setLeaveAt("14:25");
        leave.setMinutesLeft(5);
        leave.setTo("청계천");
        leave.setAtStop(true);
        return leave;
    }

    private static AlternativesResponse sampleAlternatives() {
        Alternative alternative = new Alternative();
        alternative.setKey("A");
        alternative.setShortName("시간 단축");
        alternative.setTitle("머무는 시간 줄이기");
        alternative.setReason("가는 곳은 그대로, 머무는 시간만 줄여요.");
        alternative.setRecommended(true);
        alternative.setFinalArrive("18:55");
        alternative.setSlackMin(5);
        alternative.setChanges(1);
        alternative.getCoords().add(new double[] {37.5745, 126.9849});
        alternative.getCoords().add(new double[] {37.5725, 126.9759});
        alternative.getStops().add(altStop(103L, "청계천", "관광지", 37.569, 126.978, 30, false, "14:37", 12, "60분 → 30분"));
        alternative.getStops().add(altStop(105L, "세종문화회관", "문화시설", 37.5725, 126.9759, 120, true, "19:00", 15, null));
        AlternativesResponse response = new AlternativesResponse();
        response.setTitle("세종문화회관에 늦지 않는 대안");
        response.setSubtitle("지금 위치·시각에서 다시 계산했어요");
        response.setSource("rule");
        response.getAlternatives().add(alternative);
        return response;
    }

    private static AltStop altStop(long id, String name, String category, double lat, double lng, int stay, boolean isFixed,
                                   String eta, int moveMin, String change) {
        AltStop stop = new AltStop();
        stop.setId(id);
        stop.setName(name);
        stop.setCategory(category);
        stop.setLat(lat);
        stop.setLng(lng);
        stop.setStay(stay);
        stop.setFixed(isFixed);
        stop.setEta(eta);
        stop.setMoveMin(moveMin);
        stop.setChange(change);
        stop.setClosingSoon(false);
        return stop;
    }

    private static AppliedStop appliedStop(long id, String time, String name, String category, int stay, String tag, String note) {
        AppliedStop stop = new AppliedStop();
        stop.setId(id);
        stop.setTime(time);
        stop.setName(name);
        stop.setCategory(category);
        stop.setStay(stay);
        stop.setTag(tag);
        stop.setNote(note);
        return stop;
    }
}
