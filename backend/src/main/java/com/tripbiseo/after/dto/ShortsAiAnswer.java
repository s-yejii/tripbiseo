package com.tripbiseo.after.dto;

import lombok.Data;

// Gemini가 사진 한 장을 보고 돌려주는 답 {"caption": "...", "mood": "..."}
@Data
public class ShortsAiAnswer {
    private String caption; // 한 줄 감성 자막
    private String mood;    // 잔잔함·신남·설렘·뭉클 중 하나
}
