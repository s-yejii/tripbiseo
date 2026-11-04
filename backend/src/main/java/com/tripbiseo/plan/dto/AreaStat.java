package com.tripbiseo.plan.dto;

import lombok.Data;

/** 일정 만들기 "어디로 가요?" 추천 지역 한 칸 (retrip_place를 구·종류별로 센 값) */
@Data
public class AreaStat {
    private String kind;        // cafe · food · bar · tour (문화시설은 tour에 합침)
    private String name;        // 구 이름
    private int placeCount;     // 그 구의 이 종류 장소 수
    private long reviewCount;   // 리뷰 수 합
    private double lat;         // 장소들의 가운데
    private double lng;
    private String imageUrl;    // 리뷰가 많은 장소의 사진. 없으면 null
}
