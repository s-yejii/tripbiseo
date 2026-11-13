package com.tripbiseo.live.service;

import java.util.List;

import com.tripbiseo.live.dto.PlaceAskResult;

/** ④ 여행 중 AI에게 묻기: 사용자 말을 SQL로 바꿔 장소 찾기 (Text-to-SQL) */
public interface PlaceAskService {

    /**
     * 사용자 말로 장소 찾기. AI로 못 찾으면 fallback = true (부르는 쪽이 예전 반경 고르기로).
     * prevPlaceIds가 있으면 이어지는 질문: 그 장소들 안에서만 찾음 (없으면 null)
     */
    PlaceAskResult findPlaces(long memberId, long tripId, String message, String prevMessage, List<String> prevPlaceIds,
                              double lat, double lng);
}
