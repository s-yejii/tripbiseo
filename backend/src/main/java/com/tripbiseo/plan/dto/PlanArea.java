package com.tripbiseo.plan.dto;

import java.util.List;

import lombok.Data;

/** 서울 구 지도에서 고른 구 하나 (11/04) */
@Data
public class PlanArea {
    private String name;          // 구 이름 (주소에 이 이름이 들어가면 이 구의 장소)
    private Double lat;           // 구 가운데
    private Double lng;
    private List<PlanDong> dongs; // 고른 행정동. 비어 있으면 구 전체
}
