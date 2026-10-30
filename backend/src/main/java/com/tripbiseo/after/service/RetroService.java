package com.tripbiseo.after.service;

import com.tripbiseo.after.dto.RetroResponse;

/** ⑤ 여행 후 회고 (예전에는 TripService에 있었는데 ⑤로 옮김. 주소는 그대로) */
public interface RetroService {

    /** 회고: 계획과 실제(도착·머문 시간) 비교 + GPS 선 */
    RetroResponse getRetro(long memberId, long tripId);
}
