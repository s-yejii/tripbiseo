package com.tripbiseo.plan.dto;

import lombok.Data;

/** 행정동의 특색: 종류별 장소 수와 그 구 평균보다 두드러진 종류 */
@Data
public class DongType {
    private String code;
    private int food;    // 음식점 수
    private int cafe;    // 카페 수
    private int tour;    // 볼거리 수 (관광지·문화시설)
    private String type; // food · cafe · tour, 두드러진 게 없으면 null
}
