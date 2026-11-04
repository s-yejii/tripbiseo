package com.tripbiseo.live.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tripbiseo.common.external.Route;
import com.tripbiseo.common.external.RouteClient;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.live.dto.AltStop;
import com.tripbiseo.live.dto.Alternative;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;

import lombok.RequiredArgsConstructor;

/**
 * ④ 대안 계산기: 바뀐 칸 목록을 넣으면 칸마다 이동 시간·도착 시각, 경로선, 기준 칸 도착과 여유를 계산해요.
 * AlternativeServiceImpl 안에서만 쓰는 도우미라 인터페이스 없이 @Component로 둠 (LiveAlertMaker와 같은 방식).
 */
@Component // Spring이 객체를 하나 만들어 두고, 필요한 곳에 생성자 주입으로 넣어 줌
@RequiredArgsConstructor
public class AlternativeCalculator {

    private final RouteClient routeClient;       // 이동 시간 (후보 비교는 isPrecise=false)
    private final LiveStopFinder liveStopFinder; // 출발·시작 시각, 머무는 시간 (④ 도우미)

    /** 바뀐 칸 목록으로 대안 하나 계산: 칸마다 이동 시간·도착 시각, 경로선, 기준 칸 도착과 여유 (전부 거리로 추정) */
    public Alternative calculate(Trip trip, Stop current, List<Stop> stops, double lat, double lng) {
        return calculate(trip, current, stops, lat, lng, 0);
    }

    /**
     * 위와 같은데 앞에서 preciseCount 구간은 진짜 길찾기 API로 계산.
     * 사용자가 고른 일정(/live/preview)은 여행 중 화면(/live/status)과 같은 방식으로 계산해야 두 화면 숫자가 맞음
     */
    public Alternative calculate(Trip trip, Stop current, List<Stop> stops, double lat, double lng, int preciseCount) {
        // 1. 출발점: 지금 어떤 칸에 있으면 그 칸에서 (머무는 시간이 끝나면 출발), 이동 중이면 내 위치에서 지금 출발
        double fromLat = lat;
        double fromLng = lng;
        int clock = Geo.nowMinutes();
        if (current != null) {
            fromLat = current.getLat();
            fromLng = current.getLng();
            clock = Math.max(clock, liveStopFinder.getLeaveMinutes(current));
        }

        // 2. 칸마다 이동 시간·도착 시각 (앞 preciseCount 구간만 길찾기 API, 나머지는 거리로 추정)
        Alternative alternative = new Alternative();
        List<Integer> arriveList = new ArrayList<>();
        for (Stop stop : stops) {
            boolean isPrecise = arriveList.size() < preciseCount;
            Route route = routeClient.getRoute(fromLat, fromLng, stop.getLat(), stop.getLng(), stop.getMode(), trip.getTransports(), isPrecise);
            int arrive = clock + route.getMinutes();
            arriveList.add(arrive);
            alternative.getStops().add(toAltStop(stop, arrive, route.getMinutes()));
            if (route.getCoords() != null) {
                alternative.getCoords().addAll(route.getCoords());
            }
            clock = liveStopFinder.getStartMinutes(stop, arrive) + liveStopFinder.getStay(stop);
            fromLat = stop.getLat();
            fromLng = stop.getLng();
        }

        // 3. 기준 칸(첫 고정 일정, 없으면 마지막 칸)에 몇 시 도착, 여유 몇 분
        setFinalArrive(alternative, stops, arriveList);
        return alternative;
    }

    /** 칸 목록 복사본 (대안마다 따로 고쳐도 원본이 안 바뀌게) */
    public List<Stop> copyStops(List<Stop> stops) {
        List<Stop> copies = new ArrayList<>();
        for (Stop stop : stops) {
            copies.add(copyStop(stop));
        }
        return copies;
    }

    // ---------------------------------------------------------------- 도우미

    /** 기준 칸 = 첫 고정 일정, 없으면 마지막 칸. 여유(분) = 계획 시각 − 예상 도착, 음수면 지각 */
    private void setFinalArrive(Alternative alternative, List<Stop> stops, List<Integer> arriveList) {
        int basisIndex = stops.size() - 1;
        for (int i = 0; i < stops.size(); i++) {
            if (Boolean.TRUE.equals(stops.get(i).getFixed())) {
                basisIndex = i;
                break;
            }
        }
        int arrive = arriveList.get(basisIndex);
        alternative.setFinalArrive(Geo.toHhmm(arrive));
        alternative.setSlackMin(Geo.toMinutes(stops.get(basisIndex).getTime()) - arrive);
    }

    /** Stop → 화면용 AltStop. eta는 예상 도착, 고정 일정은 계획 시각 그대로 (API.md). change는 대안마다 따로 채움 */
    private AltStop toAltStop(Stop stop, int arrive, int moveMin) {
        AltStop altStop = new AltStop();
        altStop.setId(stop.getId());
        altStop.setPlaceId(stop.getPlaceId());
        altStop.setName(stop.getName());
        altStop.setCategory(stop.getCategory());
        altStop.setLat(stop.getLat());
        altStop.setLng(stop.getLng());
        altStop.setStay(stop.getStay());
        altStop.setFixed(Boolean.TRUE.equals(stop.getFixed()));
        altStop.setImportance(stop.getImportance());
        altStop.setEta(Boolean.TRUE.equals(stop.getFixed()) ? stop.getTime() : Geo.toHhmm(arrive));
        altStop.setMoveMin(moveMin);
        altStop.setClosingSoon(false);
        return altStop;
    }

    /** 칸 하나 복사본 */
    private Stop copyStop(Stop stop) {
        Stop copy = new Stop();
        copy.setId(stop.getId());
        copy.setTripId(stop.getTripId());
        copy.setDay(stop.getDay());
        copy.setPlaceId(stop.getPlaceId());
        copy.setName(stop.getName());
        copy.setCategory(stop.getCategory());
        copy.setAddress(stop.getAddress());
        copy.setLat(stop.getLat());
        copy.setLng(stop.getLng());
        copy.setTime(stop.getTime());
        copy.setStay(stop.getStay());
        copy.setFixed(stop.getFixed());
        copy.setMode(stop.getMode());
        copy.setState(stop.getState());
        copy.setImportance(stop.getImportance());
        return copy;
    }
}
