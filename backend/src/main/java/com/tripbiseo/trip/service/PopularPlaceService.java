package com.tripbiseo.trip.service;

import com.tripbiseo.trip.dto.PopularResponse;

/** ② 요즘 인기 장소 (다른 회원들의 최근 30일 일정 기록으로 계산. 신규 회원에게도 똑같이 보여 줌) */
public interface PopularPlaceService {

    /**
     * 인기 장소. lat·lng가 없으면 시범 지역 전체(seoul).
     * radius가 없으면 3km → 모자라면 5km → 그래도 모자라면 시범 지역 전체로 넓힘. radius를 주면 그 반경만
     */
    PopularResponse getPopularPlaceList(long memberId, Double lat, Double lng, Integer radius, int limit);
}
