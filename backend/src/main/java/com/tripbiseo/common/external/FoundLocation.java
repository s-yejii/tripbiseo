package com.tripbiseo.common.external;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/** 카카오로 찾은 위치 하나 (어디서 출발해요?: 역·출구·건물·주소) */
@Data
public class FoundLocation {
    private String name;      // 예: 천호역 5호선, 현대백화점 천호점
    private String address;   // 도로명 주소 (없으면 지번 주소)
    private String category;  // 예: 지하철역, 백화점, 주소
    private double lat;
    private double lng;
    @JsonInclude(JsonInclude.Include.NON_NULL) // 내 위치를 보낸 경우에만
    private Integer distance; // 내 위치에서 거리(m)
}
