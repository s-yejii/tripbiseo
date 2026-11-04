package com.tripbiseo.plan.dto;

import java.util.List;

import lombok.Data;

/** Gemini 답: 장소마다 요청에 맞는지 판정 목록 (11/04) */
@Data
public class AiNeedJudge {
    private List<AiNeedVerdict> results;
}
