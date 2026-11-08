package com.tripbiseo.live.service;

import com.tripbiseo.live.dto.PlaceAskResult;

/** ④ 여행 중 AI에게 묻기: 사용자 말을 SQL로 바꿔 장소 찾기 (Text-to-SQL) */
public interface PlaceAskService {

    /** 사용자 말로 장소 찾기. AI로 못 찾으면 fallback = true (부르는 쪽이 예전 반경 고르기로) */
    PlaceAskResult findPlaces(long memberId, long tripId, String message, double lat, double lng);
}
