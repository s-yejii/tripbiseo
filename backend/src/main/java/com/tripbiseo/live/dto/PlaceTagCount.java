package com.tripbiseo.live.dto;

import lombok.Data;

/** 장소 하나의 리뷰 태그 개수 (PlaceTagMapper가 retrip_review를 세서 채움) */
@Data
public class PlaceTagCount {
    private String placeId;
    private int total;   // 리뷰 수
    private int solo;    // 혼밥·혼자 먹
    private int quiet;   // 조용
    private int mood;    // 분위기 좋
    private int date;    // 데이트
    private int value;   // 가성비
    private int noWait;  // 웨이팅 없·대기 없
}
