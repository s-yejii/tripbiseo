package com.tripbiseo.live.service;

import java.util.List;

import com.tripbiseo.live.dto.AlternativesRequest;
import com.tripbiseo.live.dto.AlternativesResponse;
import com.tripbiseo.live.dto.ApplyRequest;
import com.tripbiseo.live.dto.ApplyResponse;
import com.tripbiseo.live.dto.Alternative;
import com.tripbiseo.live.dto.PreviewRequest;
import com.tripbiseo.live.dto.SwapCandidate;

/**
 * ④ 대안 만들기·적용 (LiveService에서 나눔).
 * 공통 오류 (검사 순서대로): 400 "내 위치를 먼저 찾아 주세요." → 404 "여행을 찾을 수 없어요."
 *   → 400 "진행 중인 여행이 아니에요. 여행 화면에서 '여행 시작'을 눌러 주세요."
 */
public interface AlternativeService {

    /** 대안 A·B·C 만들기 (저장 안 함). Gemini가 실패하면 규칙 대안만 */
    AlternativesResponse makeAlternatives(long memberId, long tripId, AlternativesRequest request);

    /** 고른 대안을 일정에 반영. 대안에 없는 남은 칸은 skipped */
    ApplyResponse applyAlternative(long memberId, long tripId, ApplyRequest request);

    /** 사용자가 고친 남은 일정(머묾 줄이기·빼기·바꾸기)을 계산만 함 (저장 안 함) */
    Alternative previewAlternative(long memberId, long tripId, PreviewRequest request);

    /** 남은 칸 하나를 바꿀 장소 후보: 가까운 순 최대 10곳, 바꿨을 때 여유까지 */
    List<SwapCandidate> getSwapCandidateList(long memberId, long tripId, long stopId, Double lat, Double lng);
}
