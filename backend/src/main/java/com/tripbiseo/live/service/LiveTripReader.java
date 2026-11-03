package com.tripbiseo.live.service;

import static com.tripbiseo.common.exception.ApiException.fail;

import org.springframework.stereotype.Component;

import com.tripbiseo.trip.dto.Trip;
import com.tripbiseo.trip.service.TripService;

import lombok.RequiredArgsConstructor;

/**
 * ④ 여행 중 API 공통 입구: API.md "공통 오류"를 검사 순서대로 확인하고 진행 중인 여행을 돌려줘요.
 *   1. 400 "내 위치를 먼저 찾아 주세요." (lat·lng 없음)
 *   2. 404 "여행을 찾을 수 없어요." (②의 getTrip이 던짐)
 *   3. 400 "진행 중인 여행이 아니에요. …" (status ≠ ongoing)
 * track·status·alternatives·apply·assist·recommend·insert가 모두 같은 순서라 한 곳에 둠.
 */
@Component
@RequiredArgsConstructor
public class LiveTripReader {

    private final TripService tripService;

    /** 공통 오류를 검사하고 진행 중인 여행(일정 칸 포함)을 돌려줌 */
    public Trip getOngoingTrip(long memberId, long tripId, Double lat, Double lng) {
        if (lat == null || lng == null) {
            throw fail(400, "내 위치를 먼저 찾아 주세요.");
        }
        Trip trip = tripService.getTrip(memberId, tripId);
        if (!"ongoing".equals(trip.getStatus())) {
            throw fail(400, "진행 중인 여행이 아니에요. 여행 화면에서 '여행 시작'을 눌러 주세요.");
        }
        return trip;
    }
}
