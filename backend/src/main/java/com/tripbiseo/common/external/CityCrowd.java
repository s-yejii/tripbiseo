package com.tripbiseo.common.external;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** 서울 실시간 도시데이터 인구 혼잡도 결과 (CityCrowdClient가 만듦). 장소 하나의 "지금" 값 */
@Data
public class CityCrowd {
    private String source = "서울 실시간 도시데이터";
    private String areaCode;      // 장소 코드 (예: POI091)
    private String areaName;      // 장소 이름 (예: 남산공원)
    private String level;         // 여유 / 보통 / 약간 붐빔 / 붐빔
    private String message;       // 서울시가 붙인 한 줄 설명
    private Integer peopleMin;    // 인원 범위 최소
    private Integer peopleMax;    // 인원 범위 최대
    private String time;          // 서울시 기준 시각 (yyyy-MM-dd HH:mm)
    private List<Hour> forecast = new ArrayList<>(); // 앞으로 12시간 예측

    /** 한 시간 예측 */
    @Data
    public static class Hour {
        private String time;      // yyyy-MM-dd HH:mm
        private String level;     // 여유 / 보통 / 약간 붐빔 / 붐빔
        private Integer peopleMin;
        private Integer peopleMax;
    }
}
