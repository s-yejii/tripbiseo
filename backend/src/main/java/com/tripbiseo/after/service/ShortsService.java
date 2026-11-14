package com.tripbiseo.after.service;

import com.tripbiseo.after.dto.ShortsResponse;

/** ⑤ 추억 쇼츠: 사진마다 AI 감성 자막 + 여행 분위기 */
public interface ShortsService {

    /** 한 여행의 쇼츠 자막·분위기 (처음엔 Gemini로 만들고, 다음부터는 만든 것을 그대로. 남의 여행이면 404) */
    ShortsResponse getShorts(long memberId, long tripId);
}
