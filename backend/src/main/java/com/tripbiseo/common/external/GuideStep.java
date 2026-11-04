package com.tripbiseo.common.external;

import lombok.Data;

/** 길 안내 한 단계 (어디서 무엇을 하는지). RouteGuideClient가 TMAP·ODsay 응답에서 만듦 */
@Data
public class GuideStep {
    private Double lat;         // 이 안내를 할 지점
    private Double lng;
    private String kind;        // start / straight / left / right / crosswalk / stairs / walk / bus / subway / getoff / car / end
    private String text;        // "좌회전 후 50m 이동", "버스 470 타기"
    private String detail;      // "광화문 → 서울역 · 4정거장" (없으면 null)
    private boolean key;        // 놓치면 안 되는 단계 (타기·갈아타기)
}
