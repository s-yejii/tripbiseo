package com.tripbiseo.live.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tripbiseo.common.external.Route;
import com.tripbiseo.common.external.RouteClient;
import com.tripbiseo.live.dto.AskSituation;
import com.tripbiseo.live.dto.AskedPlace;

import lombok.RequiredArgsConstructor;

/**
 * ④ AI에게 묻기: AI SQL이 직선으로 고른 후보를 실제 걷는 시간으로 다시 재고 줄 세움 (11/13).
 * 왜 두 단계인지: 넓게 거르기는 DB 공간 인덱스로 빠르게(직선), 마지막 몇 곳만 길찾기 API로 정확하게.
 * 직선으로 가까워도 길·건물 때문에 실제로는 더 멀 수 있어서예요.
 * 길찾기가 실패하면 RouteClient가 거리로 어림한 값을 줘요 (source = "estimate") → isEstimate = true.
 */
@Component
@RequiredArgsConstructor
public class WalkRanker {

    private static final String WALK = "walk";
    private static final String ESTIMATE = "estimate";

    private final RouteClient routeClient; // 길찾기 (공통). 같은 구간은 10분 동안 기억해서 다시 안 부름

    /**
     * 다시 재고 줄 세우기.
     * orderBy "detour": 돌아가는 분 = (나 → 이곳) + (이곳 → 다음 장소) − (나 → 다음 장소), 적은 순
     * orderBy "near": 나 → 이곳 걷는 시간, 적은 순
     */
    public List<AskedPlace> rank(List<AskedPlace> places, AskSituation situation, String orderBy) {
        if ("detour".equals(orderBy)) {
            if (situation.getNextLat() == null) {
                return places; // 다음 장소가 없으면 돌아가는 분을 잴 수 없음 (SQL 순서 그대로)
            }
            measureDetour(places, situation);
            places.sort(Comparator.comparing(AskedPlace::getDetourMinutes)); // 돌아가는 분이 적은 순
        } else if ("near".equals(orderBy)) {
            measureWalk(places, situation);
            places.sort(Comparator.comparing(AskedPlace::getWalkMinutes));   // 걷는 시간이 짧은 순
        }
        return places;
    }

    /** 장소마다 실제 걷는 길로 돌아가는 분을 다시 잼 */
    private void measureDetour(List<AskedPlace> places, AskSituation situation) {
        Route direct = walk(situation.getMeLat(), situation.getMeLng(), situation.getNextLat(), situation.getNextLng());
        for (AskedPlace place : places) {
            Route toPlace = walk(situation.getMeLat(), situation.getMeLng(), place.getLat(), place.getLng());
            Route toNext = walk(place.getLat(), place.getLng(), situation.getNextLat(), situation.getNextLng());
            int detour = toPlace.getMinutes() + toNext.getMinutes() - direct.getMinutes();
            place.setDetourMinutes(Math.max(0, detour)); // 길 계산 오차로 음수가 나오면 0 (들러도 거의 안 돌아감)
            place.setWalkMinutes(toPlace.getMinutes());
            place.setIsEstimate(isEstimate(direct) || isEstimate(toPlace) || isEstimate(toNext));
        }
    }

    /** 장소마다 지금 위치에서 걷는 시간을 잼 */
    private void measureWalk(List<AskedPlace> places, AskSituation situation) {
        for (AskedPlace place : places) {
            Route toPlace = walk(situation.getMeLat(), situation.getMeLng(), place.getLat(), place.getLng());
            place.setWalkMinutes(toPlace.getMinutes());
            place.setIsEstimate(isEstimate(toPlace));
        }
    }

    /** 걸어서 길찾기 (isPrecise = true: API로 실제 길) */
    private Route walk(double lat1, double lng1, double lat2, double lng2) {
        return routeClient.getRoute(lat1, lng1, lat2, lng2, WALK, List.of(WALK), true);
    }

    /** 길찾기 API가 실패해서 거리로 어림한 값인지 */
    private boolean isEstimate(Route route) {
        return ESTIMATE.equals(route.getSource());
    }
}
