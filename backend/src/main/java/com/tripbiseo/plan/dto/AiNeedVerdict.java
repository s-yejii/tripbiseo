package com.tripbiseo.plan.dto;

import lombok.Data;

/** Gemini 답 한 줄: 이 장소가 요청에 맞는지 + 근거 리뷰 글 그대로 (11/04) */
@Data
public class AiNeedVerdict {
    private String id;      // 후보 장소 id
    private String verdict; // ok(맞음) · bad(안 맞음) · unknown(모름)
    private String quote;   // 근거가 된 리뷰 글 (서버가 진짜 리뷰에 있는지 다시 확인)
}
