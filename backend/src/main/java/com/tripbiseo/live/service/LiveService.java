package com.tripbiseo.live.service;

import com.tripbiseo.live.dto.AssistRequest;
import com.tripbiseo.live.dto.AssistResponse;
import com.tripbiseo.live.dto.InsertRequest;
import com.tripbiseo.live.dto.InsertResponse;
import com.tripbiseo.live.dto.RecommendRequest;
import com.tripbiseo.live.dto.RecommendResponse;

/**
 * ④ 여행 중 판별: 채팅, 추천, 일정에 넣기. 지금 상황은 StatusService, GPS 저장·도착 판정은 TrackService, 대안은 AlternativeService.
 * 모든 메서드 공통 오류 (검사 순서대로): 400 "내 위치를 먼저 찾아 주세요." → 404 "여행을 찾을 수 없어요."
 *   → 400 "진행 중인 여행이 아니에요. 여행 화면에서 '여행 시작'을 눌러 주세요."
 */
public interface LiveService {

    /** 여행 중 채팅: Gemini가 말을 분류 (추천 / 일정 바꾸기 / 질문). 실패하면 낱말 규칙 */
    AssistResponse assist(long memberId, long tripId, AssistRequest request);

    /** 반경 안 평점 상위 3곳 추천 */
    RecommendResponse recommend(long memberId, long tripId, RecommendRequest request);

    /** 추천 장소를 바로 다음 일정으로 넣고 남은 시각 다시 계산 */
    InsertResponse addRecommendedPlace(long memberId, long tripId, InsertRequest request);
}
