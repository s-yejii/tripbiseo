package com.tripbiseo.plan.dto;

import lombok.Data;

/** 고른 행정동 하나 (11/04) */
@Data
public class PlanDong {
    private String name;
    private String geometry; // 경계 GeoJSON 글자. 이 안의 장소를 찾을 때만 쓰고 저장하지 않음 (Gemini에도 안 보냄)
}
