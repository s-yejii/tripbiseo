package com.tripbiseo.live.service;

import com.tripbiseo.live.dto.LocationRequest;
import com.tripbiseo.live.dto.StatusResponse;

/**
 * ④ 지금 상황 다시 계산 (예전 LiveService에서 나눔).
 * 공통 오류 (검사 순서대로): 400 "내 위치를 먼저 찾아 주세요." → 404 "여행을 찾을 수 없어요."
 *   → 400 "진행 중인 여행이 아니에요. 여행 화면에서 '여행 시작'을 눌러 주세요."
 */
public interface StatusService {

    /** 지금 상황 다시 계산 (알림·여유·날씨) */
    StatusResponse getStatus(long memberId, long tripId, LocationRequest request);
}
