package com.tripbiseo.plan.dto;

import java.util.List;

import lombok.Data;

/** Gemini 답: 요청("4명이 앉을 자리") → 리뷰에서 찾을 짧은 단어 (단체석·단체·넓어) (11/04) */
@Data
public class AiNeedWords {
    private List<String> words;
}
