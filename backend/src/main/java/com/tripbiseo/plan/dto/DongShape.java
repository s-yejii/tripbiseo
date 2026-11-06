package com.tripbiseo.plan.dto;

import lombok.Data;

/** 행정동 하나의 코드와 경계 (POST /api/plans/dong-types 요청) */
@Data
public class DongShape {
    private String code;     // 행정동 코드
    private String geometry; // 경계 GeoJSON 글자
}
