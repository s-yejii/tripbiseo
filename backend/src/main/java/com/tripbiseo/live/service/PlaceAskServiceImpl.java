package com.tripbiseo.live.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.tripbiseo.common.ai.AiService;
import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.common.external.RouteClient;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.live.dto.AskSituation;
import com.tripbiseo.live.dto.AskedPlace;
import com.tripbiseo.live.dto.LocationRequest;
import com.tripbiseo.live.dto.NextRoute;
import com.tripbiseo.live.dto.PlaceAskResult;
import com.tripbiseo.live.dto.SqlAnswer;
import com.tripbiseo.live.dto.SqlCheck;
import com.tripbiseo.live.dto.StatusResponse;
import com.tripbiseo.trip.dto.OpenInfo;
import com.tripbiseo.trip.dto.Place;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;
import com.tripbiseo.trip.service.PlaceService;

import lombok.RequiredArgsConstructor;

/**
 * ④ 여행 중 AI에게 묻기 (Text-to-SQL).
 * 순서: 상황 모으기 → Gemini가 SQL 작성 → SqlChecker 검사(떨어지면 한 번 더) → AskSqlRunner로 실행 → 영업·여유 계산.
 * 어디서 막히면 fallback = true를 돌려주고, 부르는 쪽(LiveServiceImpl.assist)이 예전 반경 고르기로 이어 가요.
 * (여기서 예전 추천을 직접 부르지 않는 이유: LiveServiceImpl이 이 서비스를 부르니 서로 부르는 구조가 됨)
 * 좌표는 Gemini에 보내지 않아요. SQL에는 빈칸 이름만 있고 값은 서버가 넣어요.
 */
@Service
@RequiredArgsConstructor
public class PlaceAskServiceImpl implements PlaceAskService {

    private static final int STAY_MINUTES = 30;  // 들르면 머무는 시간(분). 프롬프트 규칙과 같음
    private static final int BAND_METERS = 190;  // 지도 띠 폭: 가는 길 양옆 190m = 걸어서 왕복 약 5분
    private static final String ROUTE = "route";
    private static final String ME = "me";
    private static final String NEXT = "next";
    private static final String SLACK = "slack";

    private final LiveTripReader liveTripReader;   // 진행 중인 여행인지 확인 (④ 도우미)
    private final StatusService statusService;     // 다음 칸·가는 길·여유 (④)
    private final RouteClient routeClient;         // 걸어가는 구간인지 (공통)
    private final AiService aiService;             // Gemini (공통)
    private final SqlPromptMaker sqlPromptMaker;
    private final SqlChecker sqlChecker;
    private final AskSqlRunner askSqlRunner;
    private final PlaceService placeService;       // 영업 중인지 (②)

    /** 사용자 말로 장소 찾기 */
    @Override
    public PlaceAskResult findPlaces(long memberId, long tripId, String message, double lat, double lng) {
        Trip trip = liveTripReader.getOngoingTrip(memberId, tripId, lat, lng);
        StatusResponse status = statusService.getStatus(memberId, tripId, toLocation(lat, lng));
        AskSituation situation = collectSituation(trip, status, lat, lng);
        Set<String> usable = findUsable(situation);

        SqlAnswer answer = askSql(message, usable, null);
        if (answer == null) {
            return makeFallback();
        }
        if (!Boolean.TRUE.equals(answer.getSupported())) {
            return makeUnsupported(answer);
        }
        SqlCheck check = checkAnswer(answer, usable);
        if (!check.isOk()) {
            answer = askSql(message, usable, check.getReason()); // 한 번 더: 떨어진 이유를 붙여서
            check = answer == null ? check : checkAnswer(answer, usable);
        }
        if (answer == null || !check.isOk()) {
            return makeFallback();
        }
        return runAndFinish(answer, check, situation);
    }

    // ---------------------------------------------------------------- 1. 상황 모으기

    /** 빈칸 값: 내 위치는 항상, 다음 칸·여유는 남은 일정이 있으면, 가는 길은 걸어가는 구간일 때만 */
    private AskSituation collectSituation(Trip trip, StatusResponse status, double lat, double lng) {
        AskSituation situation = new AskSituation();
        situation.setMe(toPoint(lat, lng));
        if (status.getArea() != null) {
            situation.setSlack(status.getArea().getSlackMin());
        }
        Stop nextStop = findNextStop(trip, status.getNext());
        if (nextStop == null) {
            return situation;
        }
        situation.setNext(toPoint(nextStop.getLat(), nextStop.getLng()));
        List<double[]> coords = status.getNext().getCoords();
        boolean hasRoute = coords != null && coords.size() >= 2;
        if (hasRoute && isWalkingToNext(trip, nextStop, lat, lng)) {
            situation.setRoute(toLine(coords));
        }
        return situation;
    }

    /** 지금 상황의 다음 칸 (좌표 없으면 null) */
    private Stop findNextStop(Trip trip, NextRoute next) {
        if (next == null || next.getId() == null) {
            return null;
        }
        for (Stop stop : trip.getStops()) {
            if (next.getId().equals(stop.getId()) && stop.getLat() != null && stop.getLng() != null) {
                return stop;
            }
        }
        return null;
    }

    /** 다음 칸까지 걸어가는지 (길찾기와 같은 규칙: 고른 수단, 없으면 700m 안은 도보). 대중교통이면 "가는 길" 근처는 의미 없음 */
    private boolean isWalkingToNext(Trip trip, Stop nextStop, double lat, double lng) {
        double meters = Geo.distanceMeters(lat, lng, nextStop.getLat(), nextStop.getLng());
        return "walk".equals(routeClient.chooseMode(nextStop.getMode(), meters, trip.getTransports()));
    }

    /** 지금 값이 있는 빈칸 (프롬프트에 "지금 쓸 수 있는 빈칸"으로 알려 줌) */
    private Set<String> findUsable(AskSituation situation) {
        Set<String> usable = new LinkedHashSet<>();
        if (situation.getRoute() != null) {
            usable.add(ROUTE);
        }
        usable.add(ME);
        if (situation.getNext() != null) {
            usable.add(NEXT);
        }
        if (situation.getSlack() != null) {
            usable.add(SLACK);
        }
        return usable;
    }

    // ---------------------------------------------------------------- 2·3. SQL 받기·검사

    /** Gemini에게 SQL 받기. 키가 없거나 실패하면 null */
    private SqlAnswer askSql(String message, Set<String> usable, String retryReason) {
        if (!aiService.isReady()) {
            return null;
        }
        try {
            return aiService.askJsonQuick(sqlPromptMaker.makePrompt(message, usable, retryReason), SqlAnswer.class);
        } catch (ApiException e) {
            return null;
        }
    }

    /** SqlChecker 검사 + 지금 값이 없는 빈칸을 썼는지 (예: 대중교통 구간인데 :route) */
    private SqlCheck checkAnswer(SqlAnswer answer, Set<String> usable) {
        SqlCheck check = sqlChecker.checkSql(answer.getSql());
        if (!check.isOk()) {
            return check;
        }
        for (String name : check.getPlaceholders()) {
            if (!usable.contains(name)) {
                SqlCheck fail = new SqlCheck();
                fail.setOk(false);
                fail.setReason("지금 쓸 수 없는 빈칸 :" + name + " (지금 쓸 수 있는 빈칸만 써)");
                return fail;
            }
        }
        return check;
    }

    // ---------------------------------------------------------------- 4·5. 실행·후처리

    /** 실행하고 영업·여유를 붙여 결과로. DB가 막으면(3초 초과·권한 밖) fallback */
    private PlaceAskResult runAndFinish(SqlAnswer answer, SqlCheck check, AskSituation situation) {
        List<AskedPlace> places;
        try {
            places = askSqlRunner.run(check, situation);
        } catch (DataAccessException e) { // DataAccessException: 스프링이 DB 오류를 감싸는 예외
            return makeFallback();
        }
        List<AskedPlace> openPlaces = addOpenAndSlack(places, situation.getSlack());
        String band = null;
        if (check.getPlaceholders().contains(ROUTE)) {
            band = askSqlRunner.getRouteBand(situation.getRoute(), BAND_METERS);
        }
        return makeResult(answer, situation.getSlack(), band, openPlaces);
    }

    /** 지금 닫혀 있는 곳은 빼고, 닫는 시각·들르고 남는 여유를 채움 */
    private List<AskedPlace> addOpenAndSlack(List<AskedPlace> places, Integer slack) {
        List<AskedPlace> openPlaces = new ArrayList<>();
        for (AskedPlace place : places) {
            OpenInfo open = placeService.getOpenInfo(toPlace(place), Geo.now().toLocalDate(), Geo.nowMinutes(), STAY_MINUTES);
            if (open != null && Boolean.FALSE.equals(open.getOpen())) {
                continue; // 휴무거나 닫을 시간
            }
            if (open != null) {
                place.setCloseAt(open.getCloseAt());
                place.setClosingSoon(open.getClosingSoon());
            }
            place.setSlackAfter(getSlackAfter(slack, place.getDetourMinutes()));
            openPlaces.add(place);
        }
        return openPlaces;
    }

    /** 들르고 남는 여유 = 지금 여유 − (돌아가는 분 + 머무는 분). 여유나 돌아가는 분을 모르면 null */
    private Integer getSlackAfter(Integer slack, Integer detourMinutes) {
        if (slack == null || detourMinutes == null) {
            return null;
        }
        return slack - detourMinutes - STAY_MINUTES;
    }

    // ---------------------------------------------------------------- 결과

    private PlaceAskResult makeResult(SqlAnswer answer, Integer slack, String band, List<AskedPlace> places) {
        PlaceAskResult result = new PlaceAskResult();
        result.setMessage(places.isEmpty() ? "조건에 맞는 곳을 찾지 못했어요. 조건을 바꿔서 물어봐 주세요." : places.size() + "곳을 찾았어요");
        result.setExplain(answer.getExplain());
        result.setSql(answer.getSql());
        result.setSlack(slack);
        result.setBand(band);
        result.setPlaces(places);
        return result;
    }

    /** 표에 없는 조건(가격·메뉴 등)이라 찾을 수 없음 → 이유만 안내 */
    private PlaceAskResult makeUnsupported(SqlAnswer answer) {
        PlaceAskResult result = new PlaceAskResult();
        result.setMessage(answer.getReason() == null ? "그 조건으로는 찾을 수 없어요." : answer.getReason());
        return result;
    }

    /** AI로 찾지 못함 → 부르는 쪽이 예전 반경 고르기로 */
    private PlaceAskResult makeFallback() {
        PlaceAskResult result = new PlaceAskResult();
        result.setFallback(true);
        result.setMessage("질문을 다 알아듣지 못해서, 범위를 골라 찾을게요.");
        return result;
    }

    // ---------------------------------------------------------------- 바꾸기 도우미

    private LocationRequest toLocation(double lat, double lng) {
        LocationRequest location = new LocationRequest();
        location.setLat(lat);
        location.setLng(lng);
        return location;
    }

    /** 영업 확인(getOpenInfo)에 넘길 Place (영업시간·휴무 원문만 있으면 됨) */
    private Place toPlace(AskedPlace askedPlace) {
        Place place = new Place();
        place.setId(askedPlace.getId());
        place.setName(askedPlace.getName());
        place.setLat(askedPlace.getLat());
        place.setLng(askedPlace.getLng());
        place.setUseTime(askedPlace.getUseTime());
        place.setRestDate(askedPlace.getRestDate());
        return place;
    }

    /** 위도·경도 → 'SRID=4326;POINT(경도 위도)' (PostGIS는 경도가 먼저) */
    private String toPoint(double lat, double lng) {
        return "SRID=4326;POINT(" + lng + " " + lat + ")";
    }

    /** 길 좌표 [[위도, 경도], …] → 'SRID=4326;LINESTRING(경도 위도, …)' */
    private String toLine(List<double[]> coords) {
        StringBuilder line = new StringBuilder("SRID=4326;LINESTRING(");
        for (int i = 0; i < coords.size(); i++) {
            if (i > 0) {
                line.append(", ");
            }
            line.append(coords.get(i)[1]).append(' ').append(coords.get(i)[0]);
        }
        return line.append(')').toString();
    }
}
