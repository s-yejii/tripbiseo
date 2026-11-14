package com.tripbiseo.after.dto;

import java.util.List;

import lombok.Data;

// GET /api/trips/{id}/shorts 응답: 여행 전체 분위기 + 사진마다 자막
@Data
public class ShortsResponse {
    private String mood;                  // 잔잔함·신남·설렘·뭉클 중 하나 (나중에 BGM 고를 때 씀)
    private List<ShortsCaption> captions; // 사진 순서(찍은 순) 그대로
}
